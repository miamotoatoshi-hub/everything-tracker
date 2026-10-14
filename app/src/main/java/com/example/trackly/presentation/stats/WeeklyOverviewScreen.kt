@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.DayState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.presentation.home.StreakChip
import com.example.trackly.ui.components.BarChart
import com.example.trackly.ui.components.BarData
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.DayDot
import com.example.trackly.ui.components.EmptyState
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.SectionTitle
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import kotlin.math.roundToInt

@Composable
fun WeeklyOverviewScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit) {
    val viewModel = appViewModel { WeeklyOverviewViewModel(it.habitRepository, it.todayProvider, it.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = viewModel::retry)
                is UiState.Content -> {
                    val data = current.data
                    if (data.rows.isEmpty()) {
                        EmptyState(
                            emoji = "🗓️",
                            title = stringResource(R.string.stats_empty_title),
                            message = stringResource(R.string.stats_empty_message),
                        )
                    } else {
                        CenteredContent(maxWidth = 640.dp) { WeeklyBody(data, onOpenHabit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBody(data: WeeklyOverview, onOpenHabit: (Long) -> Unit) {
    val dates = rememberDateFormats()
    val summary = data.summary
    val isDark = TracklyTheme.extras.isDark
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("weekly_body"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "${dates.mediumDate(summary.range.start)} – ${dates.mediumDate(summary.range.end)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryNumber(
                        value = summary.habitCount.toString(),
                        label = pluralStringResource(R.plurals.weekly_habits, summary.habitCount),
                        modifier = Modifier.weight(1f).testTag("weekly_habits"),
                    )
                    SummaryNumber(
                        value = summary.completions.toString(),
                        label = pluralStringResource(R.plurals.weekly_completions, summary.completions),
                        modifier = Modifier.weight(1f).testTag("weekly_completions"),
                    )
                    SummaryNumber(
                        value = "${(summary.completionRate * 100).roundToInt()}%",
                        label = stringResource(R.string.weekly_completion_rate),
                        modifier = Modifier.weight(1f).testTag("weekly_rate"),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏆", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pluralStringResource(R.plurals.weekly_best_streak_days, summary.bestStreak, summary.bestStreak),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle(stringResource(R.string.weekly_daily_progress))
                BarChart(
                    bars = summary.days.map { day ->
                        BarData(
                            label = dates.weekdayNarrow(day.date.dayOfWeek),
                            fraction = day.fraction,
                            description = stringResource(
                                R.string.weekly_day_description,
                                dates.dayAndMonth(day.date),
                                day.completed,
                                day.scheduled,
                            ),
                            highlighted = day.date == data.today,
                            muted = day.date.isAfter(data.today),
                        )
                    },
                    barColor = accent,
                )
            }
        }

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.weekly_by_habit))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1.6f))
                    summary.range.days().forEach { date ->
                        Text(
                            text = dates.weekdayNarrow(date.dayOfWeek),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                }
                data.rows.forEach { row ->
                    val rowAccent = Color(row.habit.colorArgb).habitAccent(isDark)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1.6f)) {
                            Text("${row.habit.icon} ${row.habit.name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            if (row.streak > 0) StreakChip(streak = row.streak)
                        }
                        row.states.forEachIndexed { index, state ->
                            Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                                DayDot(
                                    state = state,
                                    color = rowAccent,
                                    size = 20.dp,
                                    isToday = summary.range.days()[index] == data.today,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            maxLines = 2,
        )
    }
}
