package com.example.trackly.billing

import com.example.trackly.BuildConfig

/**
 * Billing configuration. The values come from `gradle.properties` — change them there:
 *   BILLING_PREMIUM_PRODUCT_ID=trackly_premium_monthly
 * The price is never configured in the app: it is always read from Google Play (ProductDetails).
 */
object BillingConfig {
    val PREMIUM_PRODUCT_ID: String = BuildConfig.BILLING_PREMIUM_PRODUCT_ID

    /** Deep link into the Google Play subscription management page for this product. */
    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PREMIUM_PRODUCT_ID&package=$packageName"
}
