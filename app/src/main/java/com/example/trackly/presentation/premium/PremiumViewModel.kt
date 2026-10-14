package com.example.trackly.presentation.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.example.trackly.billing.BillingEvent
import com.example.trackly.billing.BillingRepository
import com.example.trackly.billing.ProductState
import com.example.trackly.domain.model.PremiumEntitlement
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class PremiumViewModel(private val billing: BillingRepository) : ViewModel() {
    val productState: StateFlow<ProductState> = billing.productState
    val entitlement: StateFlow<PremiumEntitlement> = billing.entitlement
    val isPremium: StateFlow<Boolean> = billing.isPremium
    val events: SharedFlow<BillingEvent> = billing.events

    init {
        // Make sure product details and purchases are fresh whenever the paywall opens.
        billing.connect()
    }

    fun purchase(activity: Activity) = billing.launchPurchase(activity)

    fun restore() = billing.restorePurchases()

    fun retry() = billing.refreshProduct()
}
