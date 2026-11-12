package com.example.trackly.billing

import com.example.trackly.BuildConfig

/**
 * Billing configuration. The values come from `gradle.properties` — change them there:
 *   BILLING_PREMIUM_PRODUCT_ID=trackly_premium            (subscription with "monthly" and "yearly" base plans)
 *   BILLING_LIFETIME_PRODUCT_ID=trackly_premium_lifetime  (one-time product; leave empty to sell no lifetime plan)
 * Prices are never configured in the app: they are always read from Google Play (ProductDetails).
 */
object BillingConfig {
    val PREMIUM_PRODUCT_ID: String = BuildConfig.BILLING_PREMIUM_PRODUCT_ID

    /** Null when no lifetime product is configured. */
    val LIFETIME_PRODUCT_ID: String? = BuildConfig.BILLING_LIFETIME_PRODUCT_ID.takeIf { it.isNotBlank() }

    /** Deep link into the Google Play subscription management page for this product. */
    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PREMIUM_PRODUCT_ID&package=$packageName"
}
