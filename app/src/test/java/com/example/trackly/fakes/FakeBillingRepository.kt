package com.example.trackly.fakes

import android.app.Activity
import com.example.trackly.billing.BillingEvent
import com.example.trackly.billing.BillingRepository
import com.example.trackly.billing.ProductState
import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.PremiumEntitlement
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Billing stand-in for unit tests: Premium is whatever the test sets. Never used in production code. */
class FakeBillingRepository(premium: Boolean = false) : BillingRepository {
    private val _entitlement = MutableStateFlow(
        if (premium) PremiumEntitlement(EntitlementStatus.ACTIVE, "test_premium", 0L) else PremiumEntitlement.None,
    )
    private val _isPremium = MutableStateFlow(premium)
    override val entitlement: StateFlow<PremiumEntitlement> = _entitlement.asStateFlow()
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    override val productState: StateFlow<ProductState> = MutableStateFlow(ProductState.Loading)
    override val events: SharedFlow<BillingEvent> = MutableSharedFlow()

    var connectCalls = 0
    var restoreCalls = 0

    fun setEntitlement(entitlement: PremiumEntitlement) {
        _entitlement.value = entitlement
        _isPremium.value = entitlement.isPremium
    }

    override fun connect() {
        connectCalls++
    }

    override fun refreshProduct() = Unit
    override fun refreshPurchases() = Unit
    override fun launchPurchase(activity: Activity) = Unit

    override fun restorePurchases() {
        restoreCalls++
    }
}
