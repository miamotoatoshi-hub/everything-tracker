package com.example.trackly.billing

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Pure helpers for presenting Google Play plans. Amounts always come from Google Play. */
object PlanPricing {

    /** The kind of a subscription base plan, from its recurring ISO-8601 billing period. */
    fun kindOf(isoPeriod: String): PlanKind {
        val period = BillingPeriod.parse(isoPeriod) ?: return PlanKind.OTHER
        return when {
            period.count == 1 && period.unit == PeriodUnit.YEAR -> PlanKind.YEARLY
            period.count == 1 && period.unit == PeriodUnit.MONTH -> PlanKind.MONTHLY
            else -> PlanKind.OTHER
        }
    }

    /** Display order on the paywall: yearly, monthly, other periods, lifetime. */
    fun sorted(plans: List<PremiumPlan>): List<PremiumPlan> = plans.sortedBy {
        when (it.kind) {
            PlanKind.YEARLY -> 0
            PlanKind.MONTHLY -> 1
            PlanKind.OTHER -> 2
            PlanKind.LIFETIME -> 3
        }
    }

    /**
     * Whole-percent saving of the yearly plan versus paying the monthly plan for 12 months, or null
     * when there is nothing honest to show (missing plan, different currencies, no real saving).
     */
    fun yearlySavingsPercent(plans: List<PremiumPlan>): Int? {
        val monthly = plans.firstOrNull { it.kind == PlanKind.MONTHLY } ?: return null
        val yearly = plans.firstOrNull { it.kind == PlanKind.YEARLY } ?: return null
        if (monthly.currencyCode != yearly.currencyCode || monthly.priceAmountMicros <= 0L) return null
        val twelveMonths = monthly.priceAmountMicros * 12.0
        val percent = ((1.0 - yearly.priceAmountMicros / twelveMonths) * 100.0).toInt()
        return percent.takeIf { it in 5..95 }
    }

    /** "≈ $1.67" per month for a yearly plan, formatted for [locale]; null for any other plan. */
    fun monthlyEquivalent(plan: PremiumPlan, locale: Locale): String? {
        if (plan.kind != PlanKind.YEARLY || plan.priceAmountMicros <= 0L) return null
        val currency = try {
            Currency.getInstance(plan.currencyCode)
        } catch (error: IllegalArgumentException) {
            return null
        }
        val format = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
        return format.format(plan.priceAmountMicros / 12.0 / 1_000_000.0)
    }
}
