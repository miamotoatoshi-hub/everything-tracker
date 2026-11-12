package com.example.trackly.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PlanPricingTest {
    private fun plan(kind: PlanKind, micros: Long, currency: String = "USD", period: String? = null) = PremiumPlan(
        id = kind.name.lowercase(),
        productId = "trackly_premium",
        kind = kind,
        formattedPrice = "x",
        priceAmountMicros = micros,
        currencyCode = currency,
        billingPeriod = period,
        freeTrialDays = null,
    )

    private val monthly = plan(PlanKind.MONTHLY, 2_990_000, period = "P1M")
    private val yearly = plan(PlanKind.YEARLY, 19_990_000, period = "P1Y")
    private val lifetime = plan(PlanKind.LIFETIME, 39_990_000)

    @Test
    fun `plan kind comes from the billing period`() {
        assertEquals(PlanKind.MONTHLY, PlanPricing.kindOf("P1M"))
        assertEquals(PlanKind.YEARLY, PlanPricing.kindOf("P1Y"))
        assertEquals(PlanKind.OTHER, PlanPricing.kindOf("P3M"))
        assertEquals(PlanKind.OTHER, PlanPricing.kindOf("garbage"))
    }

    @Test
    fun `plans are shown yearly first and lifetime last`() {
        val sorted = PlanPricing.sorted(listOf(lifetime, monthly, yearly))
        assertEquals(listOf(PlanKind.YEARLY, PlanKind.MONTHLY, PlanKind.LIFETIME), sorted.map { it.kind })
    }

    @Test
    fun `yearly savings compare against twelve monthly payments`() {
        // 19.99 vs 12 * 2.99 = 35.88 -> 44 % cheaper.
        assertEquals(44, PlanPricing.yearlySavingsPercent(listOf(monthly, yearly)))
    }

    @Test
    fun `no savings badge without both plans or in different currencies`() {
        assertNull(PlanPricing.yearlySavingsPercent(listOf(yearly)))
        assertNull(PlanPricing.yearlySavingsPercent(listOf(monthly, plan(PlanKind.YEARLY, 19_990_000, "EUR", "P1Y"))))
        // A "saving" of almost nothing is not shown.
        assertNull(PlanPricing.yearlySavingsPercent(listOf(monthly, plan(PlanKind.YEARLY, 35_000_000, period = "P1Y"))))
    }

    @Test
    fun `monthly equivalent only for the yearly plan`() {
        assertEquals("$1.67", PlanPricing.monthlyEquivalent(yearly, Locale.US))
        assertNull(PlanPricing.monthlyEquivalent(monthly, Locale.US))
        assertNull(PlanPricing.monthlyEquivalent(plan(PlanKind.YEARLY, 1_000_000, "XYZ_BAD", "P1Y"), Locale.US))
    }
}
