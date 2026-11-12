package com.example.trackly.billing

import android.app.Activity
import com.example.trackly.domain.model.PremiumEntitlement
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** What a plan buys. Yearly and monthly are base plans of one subscription; lifetime is a one-time product. */
enum class PlanKind { YEARLY, MONTHLY, LIFETIME, OTHER }

/** One way to buy Premium, with price information from Google Play (never hard-coded). */
data class PremiumPlan(
    /** Base-plan id for a subscription plan, the product id for the lifetime purchase. */
    val id: String,
    val productId: String,
    val kind: PlanKind,
    /** Localised price string exactly as Google Play wants it displayed, e.g. "$2.99". */
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    /** ISO-8601 period such as "P1M"; null for the lifetime purchase. */
    val billingPeriod: String?,
    /** Length of a free-trial phase in days, if the user is eligible for one. */
    val freeTrialDays: Int?,
)

sealed interface ProductState {
    data object Loading : ProductState

    /** [plans] is never empty and is ordered for display: yearly, monthly, other periods, lifetime. */
    data class Available(val plans: List<PremiumPlan>) : ProductState {
        /** The plan pre-selected on the paywall: the yearly plan when it exists. */
        val defaultPlan: PremiumPlan
            get() = plans.firstOrNull { it.kind == PlanKind.YEARLY } ?: plans.first()
    }

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

    /** Starts the Google Play purchase flow for the plan with this [PremiumPlan.id]. */
    fun launchPurchase(activity: Activity, planId: String)

    /** Re-checks Google Play for existing purchases and reports the outcome through [events]. */
    fun restorePurchases()
}
