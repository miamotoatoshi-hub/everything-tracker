package com.example.trackly.billing

import java.time.Period
import java.time.format.DateTimeParseException

enum class PeriodUnit { DAY, WEEK, MONTH, YEAR }

/** A Google Play billing period such as "P1M" or "P7D", parsed from its ISO-8601 form. */
data class BillingPeriod(val count: Int, val unit: PeriodUnit) {
    /** Approximate length in days (months = 30, years = 365). */
    val approxDays: Int
        get() = count * when (unit) {
            PeriodUnit.DAY -> 1
            PeriodUnit.WEEK -> 7
            PeriodUnit.MONTH -> 30
            PeriodUnit.YEAR -> 365
        }

    companion object {
        /** Returns null for periods that cannot be parsed. */
        fun parse(iso: String): BillingPeriod? {
            val period = try {
                Period.parse(iso)
            } catch (error: DateTimeParseException) {
                return null
            }
            return when {
                period.years > 0 -> BillingPeriod(period.years, PeriodUnit.YEAR)
                period.months > 0 -> BillingPeriod(period.months, PeriodUnit.MONTH)
                period.days > 0 && period.days % 7 == 0 -> BillingPeriod(period.days / 7, PeriodUnit.WEEK)
                period.days > 0 -> BillingPeriod(period.days, PeriodUnit.DAY)
                else -> null
            }
        }
    }
}
