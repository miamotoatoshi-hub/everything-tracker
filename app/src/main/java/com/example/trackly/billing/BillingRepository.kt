package com.example.trackly.billing

import android.app.Activity
import com.example.trackly.domain.model.PremiumEntitlement
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Price information from Google Play (never hard-coded). */
data class PremiumProduct(
    val productId: String,
    val title: String,
    /** Localised price string exactly as Google Play wants it displayed, e.g. "$2.99". */
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    /** ISO-8601 period such as "P1M". */
    val billingPeriod: String,
    /** Length of a free-trial phase in days, if the user is eligible for one. */
    val freeTrialDays: Int?,
)

sealed interface ProductState {
    data object Loading : ProductState
    data class Available(val product: PremiumProduct) : ProductState
    data class Unavailable(val reason: BillingProblem) : ProductState
}

enum class BillingProblem {
    /** Google Play (or its billing service) is not available on this device/account. */
    BILLING_UNAVAILABLE,

    /** The product ID is not configured / not published in Play Console. */
    PRODUCT_NOT_FOUND,

    /** Network/service error; retrying may help. */
    NETWORK,

    /** Connection to the billing service was lost. */
    DISCONNECTED,

    UNKNOWN,
}

/** One-shot results the UI shows as messages. */
sealed interface BillingEvent {
    data object PurchaseSuccess : BillingEvent
    data object PurchaseCanceled : BillingEvent
    data object PurchasePending : BillingEvent
    data object RestoreFoundPremium : BillingEvent
    data object RestoreNothingFound : BillingEvent
    data class Failure(val problem: BillingProblem) : BillingEvent
}

/**
 * Premium entitlement backed by Google Play Billing.
 * The production implementation is `PlayBillingRepository`; tests use a fake.
 */
interface BillingRepository {
    val entitlement: StateFlow<PremiumEntitlement>
    val isPremium: StateFlow<Boolean>
    val productState: StateFlow<ProductState>
    val events: SharedFlow<BillingEvent>

    /** Connects to Google Play and refreshes product details and purchases. Safe to call repeatedly. */
    fun connect()

    /** Re-queries product details (e.g. after a failure). */
    fun refreshProduct()

    /** Re-queries purchases from Google Play. */
    fun refreshPurchases()

    fun launchPurchase(activity: Activity)

    /** Re-checks Google Play for existing purchases and reports the outcome through [events]. */
    fun restorePurchases()
}
