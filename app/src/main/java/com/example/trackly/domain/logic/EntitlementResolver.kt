package com.example.trackly.domain.logic

import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.PremiumEntitlement

enum class PurchaseStateKind { PURCHASED, PENDING, UNSPECIFIED }

/** The parts of a Google Play purchase that matter for entitlement (keeps the domain free of Play types). */
data class PurchaseSnapshot(
    val productIds: List<String>,
    val state: PurchaseStateKind,
    val autoRenewing: Boolean,
)

object EntitlementResolver {
    /** How long a previously verified Premium status is trusted when Google Play cannot be reached. */
    const val OFFLINE_GRACE_MILLIS: Long = 14L * 24 * 60 * 60 * 1000

    /**
     * Derives the entitlement from the purchases Google Play reported for this user.
     * Call this only after a *successful* purchases query — an empty list then really means
     * "no active subscription".
     *
     * [productId] is the subscription; [lifetimeProductId] the optional one-time product, which wins
     * over any subscription state because it never expires.
     */
    fun resolve(
        purchases: List<PurchaseSnapshot>,
        productId: String,
        previous: PremiumEntitlement,
        nowMillis: Long,
        lifetimeProductId: String? = null,
    ): PremiumEntitlement {
        if (lifetimeProductId != null) {
            val lifetime = purchases.filter { lifetimeProductId in it.productIds }
            if (lifetime.any { it.state == PurchaseStateKind.PURCHASED }) {
                return PremiumEntitlement(EntitlementStatus.LIFETIME, lifetimeProductId, nowMillis)
            }
            if (lifetime.any { it.state == PurchaseStateKind.PENDING } &&
                purchases.none { productId in it.productIds && it.state == PurchaseStateKind.PURCHASED }
            ) {
                return PremiumEntitlement(EntitlementStatus.PENDING, lifetimeProductId, nowMillis)
            }
        }
        val matching = purchases.filter { productId in it.productIds }
        val purchased = matching.firstOrNull { it.state == PurchaseStateKind.PURCHASED }
        if (purchased != null) {
            val status = if (purchased.autoRenewing) EntitlementStatus.ACTIVE else EntitlementStatus.ACTIVE_CANCELED
            return PremiumEntitlement(status, productId, nowMillis)
        }
        if (matching.any { it.state == PurchaseStateKind.PENDING }) {
            return PremiumEntitlement(EntitlementStatus.PENDING, productId, nowMillis)
        }
        return when (previous.status) {
            EntitlementStatus.ACTIVE,
            EntitlementStatus.ACTIVE_CANCELED,
            EntitlementStatus.EXPIRED,
            EntitlementStatus.LIFETIME, // refunded: Google Play no longer reports the purchase
            ->
                PremiumEntitlement(EntitlementStatus.EXPIRED, productId, nowMillis)
            EntitlementStatus.PENDING, EntitlementStatus.NONE -> PremiumEntitlement(EntitlementStatus.NONE, null, nowMillis)
        }
    }

    /**
     * Used when Google Play could not be queried (offline, Play Store missing, ...): a previously
     * verified subscription is honoured for [OFFLINE_GRACE_MILLIS], then dropped. A verified lifetime
     * purchase never expires, so it is honoured for as long as Google Play cannot be asked.
     */
    fun whenUnverifiable(cached: PremiumEntitlement, nowMillis: Long): PremiumEntitlement {
        if (!cached.isPremium) return cached
        if (cached.isLifetime) return if (cached.verifiedAtMillis != null) cached else PremiumEntitlement.None
        val verifiedAt = cached.verifiedAtMillis ?: return PremiumEntitlement.None
        val age = nowMillis - verifiedAt
        return if (age in 0..OFFLINE_GRACE_MILLIS) cached else PremiumEntitlement.None
    }
}
