package com.example.trackly.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.trackly.R
import com.example.trackly.billing.BillingEvent
import com.example.trackly.billing.BillingProblem

@StringRes
fun BillingProblem.messageRes(): Int = when (this) {
    BillingProblem.BILLING_UNAVAILABLE -> R.string.billing_error_unavailable
    BillingProblem.PRODUCT_NOT_FOUND -> R.string.billing_error_product
    BillingProblem.NETWORK -> R.string.billing_error_network
    BillingProblem.DISCONNECTED -> R.string.billing_error_disconnected
    BillingProblem.UNKNOWN -> R.string.billing_error_unknown
}

/** User-facing text for a billing result, or null when nothing should be shown. */
fun billingMessage(context: Context, event: BillingEvent): String? = when (event) {
    BillingEvent.PurchaseSuccess -> context.getString(R.string.billing_purchase_success)
    BillingEvent.PurchaseCanceled -> context.getString(R.string.billing_purchase_canceled)
    BillingEvent.PurchasePending -> context.getString(R.string.billing_purchase_pending)
    BillingEvent.RestoreFoundPremium -> context.getString(R.string.billing_restore_found)
    BillingEvent.RestoreNothingFound -> context.getString(R.string.billing_restore_none)
    is BillingEvent.Failure -> context.getString(event.problem.messageRes())
}
