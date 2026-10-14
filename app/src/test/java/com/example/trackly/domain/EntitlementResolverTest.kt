package com.example.trackly.domain

import com.example.trackly.domain.logic.EntitlementResolver
import com.example.trackly.domain.logic.PurchaseSnapshot
import com.example.trackly.domain.logic.PurchaseStateKind
import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.PremiumEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementResolverTest {
    private val productId = "trackly_premium_monthly"
    private val now = 1_000_000_000L

    private fun purchase(
        state: PurchaseStateKind = PurchaseStateKind.PURCHASED,
        autoRenewing: Boolean = true,
        products: List<String> = listOf(productId),
    ) = PurchaseSnapshot(products, state, autoRenewing)

    @Test
    fun `active auto-renewing purchase unlocks premium`() {
        val result = EntitlementResolver.resolve(listOf(purchase()), productId, PremiumEntitlement.None, now)
        assertEquals(EntitlementStatus.ACTIVE, result.status)
        assertTrue(result.isPremium)
        assertEquals(now, result.verifiedAtMillis)
    }

    @Test
    fun `canceled but unexpired subscription stays premium`() {
        val result = EntitlementResolver.resolve(listOf(purchase(autoRenewing = false)), productId, PremiumEntitlement.None, now)
        assertEquals(EntitlementStatus.ACTIVE_CANCELED, result.status)
        assertTrue(result.isPremium)
    }

    @Test
    fun `pending purchase does not unlock premium`() {
        val result = EntitlementResolver.resolve(
            listOf(purchase(state = PurchaseStateKind.PENDING)), productId, PremiumEntitlement.None, now,
        )
        assertEquals(EntitlementStatus.PENDING, result.status)
        assertFalse(result.isPremium)
        assertTrue(result.isPending)
    }

    @Test
    fun `no purchases for a never-subscribed user stays none`() {
        val result = EntitlementResolver.resolve(emptyList(), productId, PremiumEntitlement.None, now)
        assertEquals(EntitlementStatus.NONE, result.status)
        assertFalse(result.isPremium)
    }

    @Test
    fun `no purchases after being premium means expired`() {
        val previous = PremiumEntitlement(EntitlementStatus.ACTIVE, productId, 1L)
        val result = EntitlementResolver.resolve(emptyList(), productId, previous, now)
        assertEquals(EntitlementStatus.EXPIRED, result.status)
        assertFalse(result.isPremium)
    }

    @Test
    fun `expired stays expired when there are still no purchases`() {
        val previous = PremiumEntitlement(EntitlementStatus.EXPIRED, productId, 1L)
        assertEquals(EntitlementStatus.EXPIRED, EntitlementResolver.resolve(emptyList(), productId, previous, now).status)
    }

    @Test
    fun `a failed pending purchase falls back to none`() {
        val previous = PremiumEntitlement(EntitlementStatus.PENDING, productId, 1L)
        assertEquals(EntitlementStatus.NONE, EntitlementResolver.resolve(emptyList(), productId, previous, now).status)
    }

    @Test
    fun `purchases of other products are ignored`() {
        val other = purchase(products = listOf("some_other_product"))
        val result = EntitlementResolver.resolve(listOf(other), productId, PremiumEntitlement.None, now)
        assertEquals(EntitlementStatus.NONE, result.status)
    }

    @Test
    fun `an unspecified purchase state does not unlock`() {
        val result = EntitlementResolver.resolve(
            listOf(purchase(state = PurchaseStateKind.UNSPECIFIED)), productId, PremiumEntitlement.None, now,
        )
        assertFalse(result.isPremium)
    }

    @Test
    fun `premium is honoured offline within the grace period`() {
        val cached = PremiumEntitlement(EntitlementStatus.ACTIVE, productId, now)
        val later = now + EntitlementResolver.OFFLINE_GRACE_MILLIS - 1
        assertTrue(EntitlementResolver.whenUnverifiable(cached, later).isPremium)
    }

    @Test
    fun `premium is dropped offline after the grace period`() {
        val cached = PremiumEntitlement(EntitlementStatus.ACTIVE, productId, now)
        val later = now + EntitlementResolver.OFFLINE_GRACE_MILLIS + 1
        assertFalse(EntitlementResolver.whenUnverifiable(cached, later).isPremium)
    }

    @Test
    fun `cached premium without a verification time is not trusted`() {
        val cached = PremiumEntitlement(EntitlementStatus.ACTIVE, productId, null)
        assertFalse(EntitlementResolver.whenUnverifiable(cached, now).isPremium)
    }

    @Test
    fun `a clock moved backwards invalidates the cache`() {
        val cached = PremiumEntitlement(EntitlementStatus.ACTIVE, productId, now)
        assertFalse(EntitlementResolver.whenUnverifiable(cached, now - 10_000).isPremium)
    }

    @Test
    fun `non premium cache is returned unchanged offline`() {
        val cached = PremiumEntitlement(EntitlementStatus.EXPIRED, productId, now)
        assertEquals(cached, EntitlementResolver.whenUnverifiable(cached, now + 1))
    }
}
