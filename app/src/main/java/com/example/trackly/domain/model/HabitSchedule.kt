package com.example.trackly.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ScheduleType {
    EVERY_DAY,
    WEEKDAYS,
    WEEKENDS,
    SPECIFIC_DAYS,

    /** "Custom schedule": every [HabitSchedule.intervalDays] days, counted from the habit's start date. */
    EVERY_N_DAYS,
}

/**
 * Which days a habit is expected to be done on. Days that are not scheduled never
 * break a streak and never count as missed.
 */
data class HabitSchedule(
    val type: ScheduleType = ScheduleType.EVERY_DAY,
    val days: Set<DayOfWeek> = emptySet(),
    val intervalDays: Int = 1,
) {
    fun isScheduledOn(date: LocalDate, startDate: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        return when (type) {
            ScheduleType.EVERY_DAY -> true
            ScheduleType.WEEKDAYS -> date.dayOfWeek in WEEKDAYS
            ScheduleType.WEEKENDS -> date.dayOfWeek in WEEKEND
            ScheduleType.SPECIFIC_DAYS -> date.dayOfWeek in days
            ScheduleType.EVERY_N_DAYS -> {
                val interval = intervalDays.coerceAtLeast(1)
                ChronoUnit.DAYS.between(startDate, date) % interval == 0L
            }
        }
    }

    /** Days of week on which the habit can occur (all days for interval schedules). */
    val activeDaysOfWeek: Set<DayOfWeek>
        get() = when (type) {
            ScheduleType.EVERY_DAY, ScheduleType.EVERY_N_DAYS -> ALL_DAYS
            ScheduleType.WEEKDAYS -> WEEKDAYS
            ScheduleType.WEEKENDS -> WEEKEND
            ScheduleType.SPECIFIC_DAYS -> days
        }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        val Daily = HabitSchedule(ScheduleType.EVERY_DAY)

        /** Encodes a day set as a bitmask (bit 0 = Monday). */
        fun daysToMask(days: Set<DayOfWeek>): Int =
            days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

        fun maskToDays(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}
