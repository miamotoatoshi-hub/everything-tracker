package com.example.trackly.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trackly.R
import com.example.trackly.domain.logic.DateUtils
import com.example.trackly.domain.logic.DayState
import com.example.trackly.presentation.common.rememberDateFormats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun dayStateDescription(state: DayState): String = stringResource(
    when (state) {
        DayState.COMPLETED -> R.string.day_state_completed
        DayState.PARTIAL -> R.string.day_state_partial
        DayState.MISSED -> R.string.day_state_missed
        DayState.PENDING -> R.string.day_state_pending
        DayState.FUTURE -> R.string.day_state_upcoming
        DayState.NOT_SCHEDULED -> R.string.day_state_not_scheduled
    },
)

/**
 * Month grid with per-day status (filled = done, half = partial, empty ring = missed, muted = upcoming).
 *
 * @param stateOf visual state of a date
 * @param canGoPrevious / [canGoNext] disable the chevrons; [previousLocked] shows a lock (Premium) instead
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    color: Color,
    selected: LocalDate?,
    stateOf: (LocalDate) -> DayState,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    previousLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val dates = rememberDateFormats()
    val weekDays = DateUtils.orderedWeekDays(firstDayOfWeek)
    val blanks = DateUtils.leadingBlanks(month, firstDayOfWeek)
    val cells: List<LocalDate?> = List(blanks) { null } + List(month.lengthOfMonth()) { month.atDay(it + 1) }
    val rows = cells.chunked(7)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.testTag("calendar_prev")) {
                Icon(
                    imageVector = if (previousLocked) Icons.Rounded.Lock else Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month),
                )
            }
            Text(
                text = dates.monthYear(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("calendar_month_title"),
            )
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.testTag("calendar_next")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = dates.weekdayNarrow(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp).semantics { contentDescription = dates.weekdayFull(day) },
                )
            }
        }
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                state = stateOf(date),
                                color = color,
                                isToday = date == today,
                                isSelected = date == selected,
                                description = dates.fullDate(date),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f).height(46.dp)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    state: DayState,
    color: Color,
    isToday: Boolean,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val stateText = dayStateDescription(state)
    val numberColor = when (state) {
        DayState.COMPLETED -> if (color.luminanceIsHigh()) Color(0xFF1B1B1F) else Color.White
        DayState.FUTURE, DayState.NOT_SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
            )
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "$description, $stateText"
                role = Role.Button
                this.selected = isSelected
            }
            .testTag("calendar_day_${date}"),
        contentAlignment = Alignment.Center,
    ) {
        DayDot(state = state, color = color, size = 34.dp, isToday = isToday)
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = numberColor,
        )
    }
}

private fun Color.luminanceIsHigh(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.62f

/** Small coloured legend: filled = done, half = partial, ring = missed. */
@Composable
fun CalendarLegend(color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(DayState.COMPLETED, color, stringResource(R.string.legend_done))
        LegendItem(DayState.PARTIAL, color, stringResource(R.string.legend_partial))
        LegendItem(DayState.MISSED, color, stringResource(R.string.legend_missed))
    }
}

@Composable
private fun LegendItem(state: DayState, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayDot(state = state, color = color, size = 14.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
