@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.formatValue
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.presentation.common.rememberLocale
import com.example.trackly.presentation.common.unitLabel
import com.example.trackly.presentation.habit.StatTile
import com.example.trackly.ui.components.CalendarLegend
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.DayDot
import com.example.trackly.ui.components.EmptyState
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.MonthCalendar
import com.example.trackly.ui.components.ValueDialog
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun CalendarScreen(
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel {
        CalendarViewModel(it.habitRepository, it.todayProvider, it.settingsRepository, it.premiumPolicy)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                CalendarEvent.Failed -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(onRetry = viewModel::retry)
            is UiState.Content -> {
                val data = current.data
                if (data.habits.isEmpty()) {
                    EmptyState(
                        emoji = "📅",
                        title = stringResource(R.string.calendar_empty_title),
                        message = stringResource(R.string.calendar_empty_message),
                        actionLabel = stringResource(R.string.home_create_habit),
                        onAction = onAddHabit,
                    )
                } else {
                    CenteredContent(maxWidth = 640.dp) {
                        CalendarBody(data, viewModel, onOpenHabit)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarBody(data: CalendarData, viewModel: CalendarViewModel, onOpenHabit: (Long) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = TracklyTheme.extras.isDark
    val color = data.selectedHabit?.let { Color(it.colorArgb).habitAccent(isDark) } ?: MaterialTheme.colorScheme.primary
    var editing by remember { mutableStateOf<DayRow?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = 24.dp)
            .testTag("calendar_body"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_calendar),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                FilterChip(
                    selected = data.selectedHabit == null,
                    onClick = { viewModel.selectHabit(null) },
                    label = { Text(stringResource(R.string.calendar_all_habits)) },
                    modifier = Modifier.testTag("calendar_all"),
                )
            }
            items(data.habits, key = { it.id }) { habit ->
                FilterChip(
                    selected = data.selectedHabit?.id == habit.id,
                    onClick = { viewModel.selectHabit(habit.id) },
                    label = { Text("${habit.icon} ${habit.name}") },
                    modifier = Modifier.testTag("calendar_habit_${habit.id}"),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthCalendar(
                    month = data.month,
                    firstDayOfWeek = data.firstDay,
                    today = data.today,
                    color = color,
                    selected = data.selectedDate,
                    stateOf = { date -> data.dayStates[date] ?: DayState.NOT_SCHEDULED },
                    onSelect = viewModel::selectDate,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    canGoNext = data.canGoNext,
                    previousLocked = data.previousLocked,
                )
                CalendarLegend(color = color, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                emoji = "📈",
                value = "${(data.monthRate * 100).roundToInt()}%",
                label = stringResource(R.string.calendar_completion_rate),
                modifier = Modifier.weight(1f).testTag("calendar_rate"),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                emoji = "🔥",
                value = data.currentStreak.toString(),
                label = stringResource(R.string.details_current_streak),
                modifier = Modifier.weight(1f).testTag("calendar_current_streak"),
            )
            StatTile(
                emoji = "🏆",
                value = data.bestStreak.toString(),
                label = stringResource(R.string.details_best_streak),
                modifier = Modifier.weight(1f).testTag("calendar_best_streak"),
            )
        }

        data.selectedDate?.let { date ->
            DayPanel(
                date = date,
                rows = data.dayRows,
                today = data.today,
                onOpenHabit = onOpenHabit,
                onEdit = { editing = it },
                onToggleBoolean = { row ->
                    viewModel.setValue(row.habit.id, date, if (row.state == DayState.COMPLETED) 0.0 else 1.0)
                },
            )
        }
    }

    editing?.let { row ->
        val date = data.selectedDate
        if (date != null) {
            val dates = rememberDateFormats()
            ValueDialog(
                title = stringResource(R.string.set_value_for_date, dates.mediumDate(date)),
                unit = unitLabel(row.habit),
                initialValue = row.value,
                onConfirm = {
                    viewModel.setValue(row.habit.id, date, it)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    rows: List<DayRow>,
    today: LocalDate,
    onOpenHabit: (Long) -> Unit,
    onEdit: (DayRow) -> Unit,
    onToggleBoolean: (DayRow) -> Unit,
) {
    val dates = rememberDateFormats()
    val locale = rememberLocale()
    val isDark = TracklyTheme.extras.isDark
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("day_panel"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dates.fullDate(date), style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.forEach { row ->
                val accent = Color(row.habit.colorArgb).habitAccent(isDark)
                val editable = !date.isAfter(today) && !date.isBefore(row.habit.startDate)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = row.habit.name) { onOpenHabit(row.habit.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DayDot(state = row.state, color = accent, size = 26.dp)
                    Text(row.habit.icon, style = MaterialTheme.typography.titleMedium)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.habit.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            text = if (row.habit.type == HabitType.BOOLEAN) {
                                stringResource(if (row.state == DayState.COMPLETED) R.string.status_done else R.string.status_not_done)
                            } else {
                                stringResource(
                                    R.string.progress_format,
                                    formatValue(row.value, locale),
                                    formatValue(row.goal, locale),
                                    unitLabel(row.habit),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (editable) {
                        TextButton(
                            onClick = { if (row.habit.type == HabitType.BOOLEAN) onToggleBoolean(row) else onEdit(row) },
                            modifier = Modifier.testTag("day_edit_${row.habit.id}"),
                        ) {
                            Text(
                                stringResource(if (row.habit.type == HabitType.BOOLEAN) R.string.details_toggle_day else R.string.action_edit),
                            )
                        }
                    }
                }
            }
        }
    }
}
