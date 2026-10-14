@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.habit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.DayStateResolver
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.formatValue
import com.example.trackly.presentation.common.progressText
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.presentation.common.rememberLocale
import com.example.trackly.presentation.common.scheduleSummary
import com.example.trackly.presentation.common.unitLabel
import com.example.trackly.presentation.home.StreakChip
import com.example.trackly.presentation.home.onAccent
import com.example.trackly.ui.components.BarChart
import com.example.trackly.ui.components.BarData
import com.example.trackly.ui.components.CalendarLegend
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ConfirmDialog
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.MonthCalendar
import com.example.trackly.ui.components.PremiumLockedCard
import com.example.trackly.ui.components.ProgressRing
import com.example.trackly.ui.components.SectionTitle
import com.example.trackly.ui.components.ValueDialog
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import com.example.trackly.ui.theme.habitContainer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun HabitDetailsScreen(
    habitId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onHistory: (Long) -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel(key = "habit_details_$habitId") {
        HabitDetailsViewModel(habitId, it.habitRepository, it.todayProvider, it.settingsRepository, it.premiumPolicy)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DetailsEvent.Deleted, DetailsEvent.Archived -> currentOnBack()
                is DetailsEvent.GoalReached -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(context.getString(R.string.home_goal_reached, event.habitName))
                }
                is DetailsEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                DetailsEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.error_action_failed))
            }
        }
    }

    val details = (state as? UiState.Content)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = details?.habit?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("details_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (details != null) {
                        IconButton(onClick = { onEdit(habitId) }, modifier = Modifier.testTag("details_edit")) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("details_menu")) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!details.habit.isArchived) {
                                    val paused = details.habit.isPaused
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause)) },
                                        leadingIcon = {
                                            Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.setPaused(!paused)
                                        },
                                        modifier = Modifier.testTag("menu_pause"),
                                    )
                                }
                                val archived = details.habit.isArchived
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                                    leadingIcon = {
                                        Icon(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.setArchived(!archived)
                                    },
                                    modifier = Modifier.testTag("menu_archive"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                    modifier = Modifier.testTag("menu_delete"),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = viewModel::retry)
                is UiState.Content -> {
                    val data = current.data
                    if (data == null) {
                        ErrorState(
                            title = stringResource(R.string.habit_not_found_title),
                            message = stringResource(R.string.habit_not_found_message),
                        )
                    } else {
                        CenteredContent(maxWidth = 640.dp) {
                            DetailsBody(
                                details = data,
                                month = month,
                                selected = selected,
                                viewModel = viewModel,
                                onEdit = { onEdit(habitId) },
                                onHistory = { onHistory(habitId) },
                                onUpgrade = onUpgrade,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && details != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_habit_title),
            message = stringResource(R.string.delete_habit_message, details.habit.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailsBody(
    details: HabitDetails,
    month: YearMonth,
    selected: LocalDate?,
    viewModel: HabitDetailsViewModel,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("details_body"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(details, viewModel)
        StatsGrid(details)
        CalendarCard(details, month, selected, viewModel)
        WeekCard(details)
        if (details.policy.isUnlocked(PremiumFeature.ADVANCED_CHARTS)) {
            MonthCard(details)
            YearCard(details)
        } else {
            PremiumLockedCard(
                message = stringResource(R.string.details_charts_locked),
                onUpgrade = { onUpgrade(PremiumFeature.ADVANCED_CHARTS) },
            )
        }
        InfoCard(details, onEdit)
        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("open_history"),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.details_view_history))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// region hero

@Composable
private fun HeroCard(details: HabitDetails, viewModel: HabitDetailsViewModel) {
    val habit = details.habit
    val isDark = TracklyTheme.extras.isDark
    val base = Color(habit.colorArgb)
    val accent = base.habitAccent(isDark)
    val container by animateColorAsState(
        base.habitContainer(MaterialTheme.colorScheme.surfaceContainerLowest, isDark, if (details.todayCompleted) 1f else 0.4f),
        label = "heroBackground",
    )
    val locale = rememberLocale()
    val unit = unitLabel(habit)
    var showValueDialog by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(28.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(habit.icon, fontSize = 40.sp)
            Text(
                text = scheduleSummary(habit, details.firstDay),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProgressRing(
                progress = details.todayProgress,
                color = accent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                strokeWidth = 14.dp,
                modifier = Modifier.size(168.dp).testTag("details_ring"),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (habit.type == HabitType.BOOLEAN) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (details.todayCompleted) accent else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            stringResource(if (details.todayCompleted) R.string.status_done else R.string.status_not_done),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Text(
                            text = formatValue(details.todayValue, locale),
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.testTag("details_value"),
                        )
                        Text(
                            text = stringResource(R.string.details_of_goal, formatValue(habit.goalValue, locale), unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (details.todayCompleted) {
                Text(
                    stringResource(R.string.details_goal_reached),
                    style = MaterialTheme.typography.titleSmall,
                    color = TracklyTheme.extras.success,
                )
            } else {
                Text(
                    text = progressText(habit, details.todayValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (habit.isArchived) {
                Text(stringResource(R.string.details_archived_note), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else if (habit.isPaused) {
                Text(stringResource(R.string.details_paused_note), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                FilledTonalButton(onClick = { viewModel.setPaused(false) }) { Text(stringResource(R.string.action_resume)) }
            } else if (habit.type == HabitType.BOOLEAN) {
                Button(
                    onClick = { viewModel.onToggleBoolean(details) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("details_toggle"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (details.todayCompleted) R.string.details_undo else R.string.details_mark_done))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { viewModel.onAdd(details, -1) },
                        enabled = details.todayValue > 0.0,
                        modifier = Modifier.size(56.dp).testTag("details_minus"),
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.details_decrease))
                    }
                    Button(
                        onClick = { viewModel.onAdd(details, 1) },
                        modifier = Modifier.weight(1f).height(56.dp).testTag("details_plus"),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.details_add_step, formatValue(habit.stepValue, locale), unit),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { showValueDialog = true },
                        modifier = Modifier.size(56.dp).testTag("details_set_value"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.habit_card_set_value))
                    }
                }
            }
        }
    }
    if (showValueDialog) {
        ValueDialog(
            title = stringResource(R.string.set_value_title, habit.name),
            unit = unit,
            initialValue = details.todayValue,
            onConfirm = {
                viewModel.onSetValue(details, details.today, it)
                showValueDialog = false
            },
            onDismiss = { showValueDialog = false },
        )
    }
}

// endregion

// region stats

@Composable
private fun StatsGrid(details: HabitDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                emoji = "🔥",
                value = details.streak.current.toString(),
                label = stringResource(R.string.details_current_streak),
                modifier = Modifier.weight(1f).testTag("stat_current_streak"),
            )
            StatTile(
                emoji = "🏆",
                value = details.streak.best.toString(),
                label = stringResource(R.string.details_best_streak),
                modifier = Modifier.weight(1f).testTag("stat_best_streak"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                emoji = "✅",
                value = details.totalCompletions.toString(),
                label = stringResource(R.string.details_total_completions),
                modifier = Modifier.weight(1f).testTag("stat_total"),
            )
            StatTile(
                emoji = "📈",
                value = "${(details.allTimeRate * 100).roundToInt()}%",
                label = stringResource(R.string.details_completion_rate),
                modifier = Modifier.weight(1f).testTag("stat_rate"),
            )
        }
    }
}

@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 20.sp) }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

// endregion

// region calendar

@Composable
private fun CalendarCard(
    details: HabitDetails,
    month: YearMonth,
    selected: LocalDate?,
    viewModel: HabitDetailsViewModel,
) {
    val habit = details.habit
    val isDark = TracklyTheme.extras.isDark
    val accent = Color(habit.colorArgb).habitAccent(isDark)
    val dates = rememberDateFormats()
    val locale = rememberLocale()
    val earliest = details.policy.earliestCalendarMonth(details.today)
    val previousLocked = earliest != null && month.minusMonths(1).isBefore(earliest)
    var editDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.details_calendar))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthCalendar(
                    month = month,
                    firstDayOfWeek = details.firstDay,
                    today = details.today,
                    color = accent,
                    selected = selected,
                    stateOf = { date -> DayStateResolver.resolve(habit, details.byDate[date], date, details.today) },
                    onSelect = { viewModel.selectDate(if (it == selected) null else it) },
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    canGoNext = month.isBefore(YearMonth.from(details.today)),
                    previousLocked = previousLocked,
                )
                CalendarLegend(color = accent, modifier = Modifier.padding(horizontal = 8.dp))
                if (selected != null) {
                    val entry = details.byDate[selected]
                    val editable = !selected.isBefore(habit.startDate) && !selected.isAfter(details.today)
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dates.fullDate(selected), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (habit.type == HabitType.BOOLEAN) {
                                        stringResource(if (entry?.completed == true) R.string.status_done else R.string.status_not_done)
                                    } else {
                                        stringResource(
                                            R.string.progress_format,
                                            formatValue(entry?.value ?: 0.0, locale),
                                            formatValue(entry?.goal ?: habit.goalValue, locale),
                                            unitLabel(habit),
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (editable) {
                                TextButton(
                                    onClick = {
                                        if (habit.type == HabitType.BOOLEAN) {
                                            viewModel.onSetValue(details, selected, if (entry?.completed == true) 0.0 else 1.0)
                                        } else {
                                            editDate = selected
                                        }
                                    },
                                    modifier = Modifier.testTag("calendar_edit_day"),
                                ) { Text(stringResource(if (habit.type == HabitType.BOOLEAN) R.string.details_toggle_day else R.string.action_edit)) }
                            }
                        }
                    }
                }
            }
        }
    }
    editDate?.let { date ->
        ValueDialog(
            title = stringResource(R.string.set_value_for_date, dates.mediumDate(date)),
            unit = unitLabel(habit),
            initialValue = details.byDate[date]?.value ?: 0.0,
            onConfirm = {
                viewModel.onSetValue(details, date, it)
                editDate = null
            },
            onDismiss = { editDate = null },
        )
    }
}

// endregion

// region charts

@Composable
private fun WeekCard(details: HabitDetails) {
    val habit = details.habit
    val dates = rememberDateFormats()
    val locale = rememberLocale()
    val accent = Color(habit.colorArgb).habitAccent(TracklyTheme.extras.isDark)
    val unit = unitLabel(habit)
    val bars = details.week.cells.map { cell ->
        BarData(
            label = dates.weekdayNarrow(cell.date.dayOfWeek),
            fraction = GoalProgress.fraction(cell.value, cell.goal),
            description = stringResource(
                R.string.chart_day_description,
                dates.dayAndMonth(cell.date),
                formatValue(cell.value, locale),
                formatValue(cell.goal, locale),
                unit,
            ),
            highlighted = cell.date == details.today,
            muted = cell.date.isAfter(details.today),
        )
    }
    ChartCard(
        title = stringResource(R.string.details_this_week),
        trailing = "${(details.week.completionRate * 100).roundToInt()}%",
        subtitle = stringResource(R.string.details_days_done, details.week.completedDays, details.week.eligibleDays),
    ) {
        BarChart(bars = bars, barColor = accent)
    }
}

@Composable
private fun MonthCard(details: HabitDetails) {
    val dates = rememberDateFormats()
    val accent = Color(details.habit.colorArgb).habitAccent(TracklyTheme.extras.isDark)
    val bars = details.monthWeeks.map { bucket ->
        BarData(
            label = dates.mediumDate(bucket.start),
            fraction = bucket.rate,
            description = stringResource(
                R.string.chart_bucket_description,
                dates.mediumDate(bucket.start),
                (bucket.rate * 100).roundToInt(),
            ),
            muted = bucket.eligible == 0,
        )
    }
    ChartCard(
        title = stringResource(R.string.details_this_month),
        trailing = "${(details.month.completionRate * 100).roundToInt()}%",
        subtitle = stringResource(R.string.details_days_done, details.month.completedDays, details.month.eligibleDays),
    ) {
        BarChart(bars = bars, barColor = accent, maxBarWidth = 48.dp)
    }
}

@Composable
private fun YearCard(details: HabitDetails) {
    val dates = rememberDateFormats()
    val accent = Color(details.habit.colorArgb).habitAccent(TracklyTheme.extras.isDark)
    val currentMonth = YearMonth.from(details.today)
    val bars = details.yearMonths.map { bucket ->
        val ym = YearMonth.from(bucket.start)
        BarData(
            label = dates.month(ym),
            fraction = bucket.rate,
            description = stringResource(R.string.chart_bucket_description, dates.month(ym), (bucket.rate * 100).roundToInt()),
            highlighted = ym == currentMonth,
            muted = ym.isAfter(currentMonth),
        )
    }
    ChartCard(
        title = stringResource(R.string.details_this_year),
        trailing = "${(details.year.completionRate * 100).roundToInt()}%",
        subtitle = stringResource(R.string.details_days_done, details.year.completedDays, details.year.eligibleDays),
    ) {
        BarChart(bars = bars, barColor = accent, maxBarWidth = 24.dp)
    }
}

@Composable
fun ChartCard(
    title: String,
    trailing: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle(title)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(trailing, style = MaterialTheme.typography.headlineSmall)
            }
            content()
        }
    }
}

// endregion

// region info

@Composable
private fun InfoCard(details: HabitDetails, onEdit: () -> Unit) {
    val habit = details.habit
    val dates = rememberDateFormats()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.details_about))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(stringResource(R.string.details_schedule), scheduleSummary(habit, details.firstDay))
                InfoRow(
                    stringResource(R.string.details_time_of_day),
                    stringResource(timeOfDayLabel(habit.timeOfDay)),
                )
                InfoRow(
                    stringResource(R.string.details_reminders),
                    if (habit.reminders.isEmpty()) {
                        stringResource(R.string.details_no_reminders)
                    } else {
                        habit.reminders.joinToString(", ") { dates.time(it.time) }
                    },
                )
                InfoRow(stringResource(R.string.details_started), dates.mediumDate(habit.startDate))
                if (habit.isActiveOn(details.today).not() && !habit.isPaused) {
                    Text(
                        stringResource(R.string.habit_not_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (details.streak.current > 0) StreakChip(streak = details.streak.current)
                TextButton(onClick = onEdit) { Text(stringResource(R.string.details_edit_habit)) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

// endregion
