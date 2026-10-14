package com.example.trackly.domain.model

enum class EntitlementStatus {
    /** Never subscribed (or the purchase was never completed). */
    NONE,

    /** Subscription is active and set to renew. */
    ACTIVE,

    /** Subscription is still paid for, but auto-renew was turned off; it ends at the period end. */
    ACTIVE_CANCELED,

    /** A purchase is waiting for payment confirmation. Premium is NOT unlocked yet. */
    PENDING,

    /** Was subscribed before, but Google Play no longer reports an active subscription. */
    EXPIRED,
}

/**
 * The single source of truth for "what does this user get". Always derived from
 * Google Play purchase data — never from a hand-set boolean.
 */
data class PremiumEntitlement(
    val status: EntitlementStatus = EntitlementStatus.NONE,
    val productId: String? = null,
    /** Wall-clock millis when Google Play last confirmed this status. */
    val verifiedAtMillis: Long? = null,
) {
    val isPremium: Boolean
        get() = status == EntitlementStatus.ACTIVE || status == EntitlementStatus.ACTIVE_CANCELED

    val isPending: Boolean get() = status == EntitlementStatus.PENDING

    companion object {
        val None = PremiumEntitlement()
    }
}
