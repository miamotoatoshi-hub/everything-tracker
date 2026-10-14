package com.example.trackly.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingPeriodTest {
    @Test
    fun `monthly yearly and weekly periods`() {
        assertEquals(BillingPeriod(1, PeriodUnit.MONTH), BillingPeriod.parse("P1M"))
        assertEquals(BillingPeriod(1, PeriodUnit.YEAR), BillingPeriod.parse("P1Y"))
        assertEquals(BillingPeriod(1, PeriodUnit.WEEK), BillingPeriod.parse("P1W"))
        assertEquals(BillingPeriod(3, PeriodUnit.MONTH), BillingPeriod.parse("P3M"))
    }

    @Test
    fun `day based free trial periods`() {
        assertEquals(BillingPeriod(3, PeriodUnit.DAY), BillingPeriod.parse("P3D"))
        // Seven days is reported as one week.
        assertEquals(BillingPeriod(1, PeriodUnit.WEEK), BillingPeriod.parse("P7D"))
        assertEquals(7, BillingPeriod.parse("P7D")!!.approxDays)
        assertEquals(14, BillingPeriod.parse("P2W")!!.approxDays)
    }

    @Test
    fun `approximate length in days`() {
        assertEquals(30, BillingPeriod(1, PeriodUnit.MONTH).approxDays)
        assertEquals(365, BillingPeriod(1, PeriodUnit.YEAR).approxDays)
        assertEquals(3, BillingPeriod(3, PeriodUnit.DAY).approxDays)
    }

    @Test
    fun `garbage and empty periods are rejected`() {
        assertNull(BillingPeriod.parse(""))
        assertNull(BillingPeriod.parse("monthly"))
        assertNull(BillingPeriod.parse("P0D"))
    }
}
