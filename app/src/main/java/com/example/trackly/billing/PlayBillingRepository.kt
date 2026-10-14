package com.example.trackly.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.trackly.domain.logic.EntitlementResolver
import com.example.trackly.domain.logic.PurchaseSnapshot
import com.example.trackly.domain.logic.PurchaseStateKind
import com.example.trackly.domain.model.PremiumEntitlement
import com.example.trackly.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.coroutines.resume

/**
 * Production [BillingRepository] backed by the Google Play Billing Library.
 *
 *  - connects lazily and reconnects when the service disconnects
 *  - loads the subscription's [ProductDetails] (price/currency come from Google Play, never hard-coded)
 *  - launches the purchase flow, acknowledges purchases and resolves a [PremiumEntitlement]
 *  - re-checks purchases on demand (restore) and keeps a verified cache for offline start-up
 */
class PlayBillingRepository(
    context: Context,
    private val productId: String,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : BillingRepository, PurchasesUpdatedListener {

    private val _entitlement = MutableStateFlow(PremiumEntitlement.None)
    override val entitlement: StateFlow<PremiumEntitlement> = _entitlement.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _productState = MutableStateFlow<ProductState>(ProductState.Loading)
    override val productState: StateFlow<ProductState> = _productState.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectionMutex = Mutex()
    private val refreshMutex = Mutex()

    @Volatile private var productDetails: ProductDetails? = null

    @Volatile private var offerToken: String? = null

    @Volatile private var lastPersisted: PremiumEntitlement? = null

    /** True once Google Play has answered in this session; from then on the offline cache is ignored. */
    @Volatile private var verifiedThisSession = false

    init {
        // Start from the last Play-verified status so Premium works offline right after launch.
        scope.launch {
            val cached = try {
                settings.loadCachedEntitlement()
            } catch (error: Exception) {
                PremiumEntitlement.None
            }
            lastPersisted = cached
            if (!verifiedThisSession) {
                publish(EntitlementResolver.whenUnverifiable(cached, clock.millis()), persist = false)
            }
        }
    }

    // region BillingRepository

    override fun connect() {
        scope.launch {
            loadProduct()
            refreshPurchases()
        }
    }

    override fun refreshProduct() {
        scope.launch { loadProduct() }
    }

    override fun refreshPurchases() {
        scope.launch { queryAndProcessPurchases() }
    }

    override fun launchPurchase(activity: Activity) {
        scope.launch {
            if (!ensureConnected()) {
                _events.emit(BillingEvent.Failure(BillingProblem.BILLING_UNAVAILABLE))
                return@launch
            }
            if (productDetails == null) loadProduct()
            val details = productDetails
            val token = offerToken
            if (details == null || token == null) {
                _events.emit(BillingEvent.Failure(BillingProblem.PRODUCT_NOT_FOUND))
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(token)
                            .build(),
                    ),
                )
                .build()
            // launchBillingFlow must run on the main thread.
            val result = withContext(Dispatchers.Main) { billingClient.launchBillingFlow(activity, params) }
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> Unit // outcome arrives in onPurchasesUpdated
                BillingClient.BillingResponseCode.USER_CANCELED -> _events.emit(BillingEvent.PurchaseCanceled)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    queryAndProcessPurchases()
                    _events.emit(BillingEvent.RestoreFoundPremium)
                }
                else -> _events.emit(BillingEvent.Failure(result.toProblem()))
            }
        }
    }

    override fun restorePurchases() {
        scope.launch {
            when (val outcome = queryAndProcessPurchases()) {
                is RefreshOutcome.Failed -> _events.emit(BillingEvent.Failure(outcome.problem))
                is RefreshOutcome.Success ->
                    _events.emit(
                        if (outcome.entitlement.isPremium) BillingEvent.RestoreFoundPremium else BillingEvent.RestoreNothingFound,
                    )
            }
        }
    }

    // endregion

    // region PurchasesUpdatedListener

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val outcome = queryAndProcessPurchases(extra = purchases.orEmpty())
                    if (outcome is RefreshOutcome.Success) {
                        when {
                            outcome.entitlement.isPremium -> _events.emit(BillingEvent.PurchaseSuccess)
                            outcome.entitlement.isPending -> _events.emit(BillingEvent.PurchasePending)
                        }
                    } else if (outcome is RefreshOutcome.Failed) {
                        _events.emit(BillingEvent.Failure(outcome.problem))
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> _events.emit(BillingEvent.PurchaseCanceled)
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryAndProcessPurchases()
                else -> _events.emit(BillingEvent.Failure(billingResult.toProblem()))
            }
        }
    }

    // endregion

    // region Connection

    /** Connects (with a few retries and back-off). Returns true when the client is ready. */
    private suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        if (billingClient.isReady) return@withLock true
        var backoff = INITIAL_BACKOFF_MILLIS
        repeat(MAX_CONNECT_ATTEMPTS) { attempt ->
            val result = connectOnce()
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> return@withLock true
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                -> return@withLock false // retrying will not help
            }
            if (attempt < MAX_CONNECT_ATTEMPTS - 1) {
                delay(backoff)
                backoff *= 2
            }
        }
        false
    }

    private suspend fun connectOnce(): BillingResult = suspendCancellableCoroutine { continuation ->
        try {
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) continuation.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() {
                        // The next API call notices !isReady and reconnects.
                        if (continuation.isActive) {
                            continuation.resume(
                                BillingResult.newBuilder()
                                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                    .build(),
                            )
                        }
                    }
                },
            )
        } catch (error: IllegalStateException) {
            if (continuation.isActive) {
                continuation.resume(
                    BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ERROR).build(),
                )
            }
        }
    }

    // endregion

    // region Products

    private suspend fun loadProduct() {
        if (_productState.value !is ProductState.Available) _productState.value = ProductState.Loading
        if (!ensureConnected()) {
            _productState.value = ProductState.Unavailable(BillingProblem.BILLING_UNAVAILABLE)
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = try {
            billingClient.queryProductDetails(params)
        } catch (error: Exception) {
            Log.w(TAG, "queryProductDetails failed", error)
            _productState.value = ProductState.Unavailable(BillingProblem.UNKNOWN)
            return
        }
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _productState.value = ProductState.Unavailable(result.billingResult.toProblem())
            return
        }
        val details = result.productDetailsList?.firstOrNull { it.productId == productId }
        val offer = details?.let { chooseOffer(it) }
        val phases = offer?.pricingPhases?.pricingPhaseList.orEmpty()
        val recurring = phases.lastOrNull()
        if (details == null || offer == null || recurring == null) {
            productDetails = null
            offerToken = null
            _productState.value = ProductState.Unavailable(BillingProblem.PRODUCT_NOT_FOUND)
            return
        }
        productDetails = details
        offerToken = offer.offerToken
        val firstPhase = phases.first()
        val trialDays = if (phases.size > 1 && firstPhase.priceAmountMicros == 0L) {
            BillingPeriod.parse(firstPhase.billingPeriod)?.approxDays
        } else {
            null
        }
        _productState.value = ProductState.Available(
            PremiumProduct(
                productId = details.productId,
                title = details.name,
                formattedPrice = recurring.formattedPrice,
                priceAmountMicros = recurring.priceAmountMicros,
                currencyCode = recurring.priceCurrencyCode,
                billingPeriod = recurring.billingPeriod,
                freeTrialDays = trialDays,
            ),
        )
    }

    /**
     * Prefers an offer the user is eligible for that starts with a free trial, then the plain base
     * plan, then whatever Google Play returned first.
     */
    private fun chooseOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        val withTrial = offers.firstOrNull { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            phases.size > 1 && phases.first().priceAmountMicros == 0L
        }
        return withTrial ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    // endregion

    // region Purchases

    private sealed interface RefreshOutcome {
        data class Success(val entitlement: PremiumEntitlement) : RefreshOutcome
        data class Failed(val problem: BillingProblem) : RefreshOutcome
    }

    /** Queries Google Play for the user's subscriptions, acknowledges new ones and updates the entitlement. */
    private suspend fun queryAndProcessPurchases(extra: List<Purchase> = emptyList()): RefreshOutcome =
        refreshMutex.withLock {
            if (!ensureConnected()) {
                publish(EntitlementResolver.whenUnverifiable(_entitlement.value, clock.millis()), persist = false)
                return@withLock RefreshOutcome.Failed(BillingProblem.BILLING_UNAVAILABLE)
            }
            val result = try {
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                )
            } catch (error: Exception) {
                Log.w(TAG, "queryPurchasesAsync failed", error)
                publish(EntitlementResolver.whenUnverifiable(_entitlement.value, clock.millis()), persist = false)
                return@withLock RefreshOutcome.Failed(BillingProblem.UNKNOWN)
            }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                // Could not verify: keep the last verified status (within the offline grace period).
                publish(EntitlementResolver.whenUnverifiable(_entitlement.value, clock.millis()), persist = false)
                return@withLock RefreshOutcome.Failed(result.billingResult.toProblem())
            }
            val purchases = (result.purchasesList + extra).distinctBy { it.purchaseToken }
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged && productId in it.products }
                .forEach { acknowledge(it) }

            val resolved = EntitlementResolver.resolve(
                purchases = purchases.map { it.toSnapshot() },
                productId = productId,
                previous = _entitlement.value,
                nowMillis = clock.millis(),
            )
            publish(resolved, persist = true)
            RefreshOutcome.Success(resolved)
        }

    private suspend fun acknowledge(purchase: Purchase) {
        val result = try {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        } catch (error: Exception) {
            Log.w(TAG, "acknowledgePurchase failed", error)
            return
        }
        // On failure the purchase stays unacknowledged and is retried on the next refresh
        // (Google Play refunds purchases that are not acknowledged within 3 days).
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "acknowledgePurchase returned ${result.responseCode}")
        }
    }

    private fun Purchase.toSnapshot() = PurchaseSnapshot(
        productIds = products,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseStateKind.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseStateKind.PENDING
            else -> PurchaseStateKind.UNSPECIFIED
        },
        autoRenewing = isAutoRenewing,
    )

    private suspend fun publish(entitlement: PremiumEntitlement, persist: Boolean) {
        if (persist) verifiedThisSession = true
        _entitlement.value = entitlement
        _isPremium.value = entitlement.isPremium
        if (!persist) return
        val last = lastPersisted
        val changed = last == null || last.status != entitlement.status || last.productId != entitlement.productId
        val stale = (entitlement.verifiedAtMillis ?: 0L) - (last?.verifiedAtMillis ?: 0L) > PERSIST_REFRESH_MILLIS
        if (changed || stale) {
            lastPersisted = entitlement
            try {
                settings.saveCachedEntitlement(entitlement)
            } catch (error: Exception) {
                Log.w(TAG, "Could not cache entitlement", error)
            }
        }
    }

    // endregion

    private fun BillingResult.toProblem(): BillingProblem = when (responseCode) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> BillingProblem.DISCONNECTED
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        -> BillingProblem.NETWORK
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        -> BillingProblem.BILLING_UNAVAILABLE
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingProblem.PRODUCT_NOT_FOUND
        else -> BillingProblem.UNKNOWN
    }

    private companion object {
        const val TAG = "PlayBilling"
        const val MAX_CONNECT_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MILLIS = 1_000L
        const val PERSIST_REFRESH_MILLIS = 60L * 60 * 1000
    }
}
