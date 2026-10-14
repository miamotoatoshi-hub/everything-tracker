package com.example.trackly.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.trackly.R
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.ScheduleType
import java.time.DayOfWeek

/** Human readable schedule, e.g. "Every day", "Mon, Wed, Fri" or "Every 3 days". */
@Composable
fun scheduleSummary(habit: Habit, firstDay: DayOfWeek = DayOfWeek.MONDAY): String {
    val dates = rememberDateFormats()
    val schedule = habit.schedule
    return when (schedule.type) {
        ScheduleType.EVERY_DAY -> stringResource(R.string.schedule_every_day)
        ScheduleType.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
        ScheduleType.WEEKENDS -> stringResource(R.string.schedule_weekends)
        ScheduleType.SPECIFIC_DAYS ->
            schedule.days
                .sortedBy { Math.floorMod(it.value - firstDay.value, 7) }
                .joinToString(", ") { dates.weekdayShort(it) }
        ScheduleType.EVERY_N_DAYS -> stringResource(R.string.schedule_every_n_days_value, schedule.intervalDays)
    }
}
