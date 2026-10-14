package com.example.trackly.domain.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Inclusive range of dates. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end must not be before start" }
    }

    val length: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun days(): List<LocalDate> = List(length) { start.plusDays(it.toLong()) }

    /** The range of the same length that ends the day before [start]. */
    fun previous(): DateRange = DateRange(start.minusDays(length.toLong()), start.minusDays(1))

    companion object {
        fun week(date: LocalDate, firstDayOfWeek: DayOfWeek): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            return DateRange(start, start.plusDays(6))
        }

        fun month(month: YearMonth): DateRange = DateRange(month.atDay(1), month.atEndOfMonth())

        fun year(year: Int): DateRange = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

        fun lastDays(count: Int, today: LocalDate): DateRange =
            DateRange(today.minusDays((count - 1).toLong()), today)
    }
}

enum class StatsPeriod { WEEK, MONTH, YEAR }

object DateUtils {
    /** Week days ordered starting at [first], e.g. Mon..Sun or Sun..Sat. */
    fun orderedWeekDays(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }

    fun rangeFor(period: StatsPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): DateRange =
        when (period) {
            StatsPeriod.WEEK -> DateRange.week(today, firstDayOfWeek)
            StatsPeriod.MONTH -> DateRange.month(YearMonth.from(today))
            StatsPeriod.YEAR -> DateRange.year(today.year)
        }

    /** Number of leading blank cells before day 1 of [month] in a grid starting at [first]. */
    fun leadingBlanks(month: YearMonth, first: DayOfWeek): Int {
        val dow = month.atDay(1).dayOfWeek.value
        return Math.floorMod(dow - first.value, 7)
    }
}
