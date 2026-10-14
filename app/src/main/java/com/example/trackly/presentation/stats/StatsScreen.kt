@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.HabitRate
import com.example.trackly.domain.logic.Insights
import com.example.trackly.domain.logic.StatsPeriod
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.presentation.habit.StatTile
import com.example.trackly.ui.components.BarChart
import com.example.trackly.ui.components.BarData
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.EmptyState
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LinearRate
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.PremiumLockedCard
import com.example.trackly.ui.components.ProgressRing
import com.example.trackly.ui.components.SectionTitle
import com.example.trackly.ui.components.SegmentedControl
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onAddHabit: () -> Unit,
    onOpenWeekly: () -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel {
        StatsViewModel(it.habitRepository, it.todayProvider, it.settingsRepository, it.premiumPolicy)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)

    // Choosing a locked period shows the locked preview; the upgrade is offered from that card instead
    // of being forced on the user, so the event is intentionally not navigated automatically.
    LaunchedEffect(viewModel) { viewModel.events.collect { } }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(onRetry = viewModel::retry)
            is UiState.Content -> {
                val data = current.data
                if (data.habits.isEmpty()) {
                    EmptyState(
                        emoji = "📊",
                        title = stringResource(R.string.stats_empty_title),
                        message = stringResource(R.string.stats_empty_message),
                        actionLabel = stringResource(R.string.home_create_habit),
                        onAction = onAddHabit,
                    )
                } else {
                    CenteredContent(maxWidth = 640.dp) {
                        StatsBody(data, viewModel, onOpenWeekly) { currentOnUpgrade(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsBody(
    data: StatsData,
    viewModel: StatsViewModel,
    onOpenWeekly: () -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val periods = listOf(StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)
    val locked = periods.withIndex().filter { !data.policy.isStatsPeriodAllowed(it.value) }.map { it.index }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
            .testTag("stats_body"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_statistics), style = MaterialTheme.typography.headlineMedium)

        SegmentedControl(
            options = listOf(stringResource(R.string.stats_overall), stringResource(R.string.stats_per_habit)),
            selectedIndex = if (data.selectedHabit == null) 0 else 1,
            onSelect = { index ->
                viewModel.selectHabit(if (index == 0) null else data.habits.first().id)
            },
            modifier = Modifier.testTag("stats_scope"),
        )
        if (data.selectedHabit != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.habits, key = { it.id }) { habit ->
                    FilterChip(
                        selected = data.selectedHabit.id == habit.id,
                        onClick = { viewModel.selectHabit(habit.id) },
                        label = { Text("${habit.icon} ${habit.name}") },
                    )
                }
            }
        }

        SegmentedControl(
            options = listOf(
                stringResource(R.string.stats_week),
                stringResource(R.string.stats_month),
                stringResource(R.string.stats_year),
            ),
            selectedIndex = periods.indexOf(data.period),
            onSelect = { viewModel.selectPeriod(periods[it]) },
            lockedIndices = locked,
            modifier = Modifier.testTag("stats_period"),
        )

        if (data.locked) {
            PremiumLockedCard(
                message = stringResource(R.string.stats_locked_message),
                onUpgrade = { onUpgrade(PremiumFeature.ADVANCED_STATISTICS) },
                modifier = Modifier.testTag("stats_locked"),
            )
        } else {
            SummaryCard(data)
            MetricGrid(data)
            ActivityCard(data)
            if (data.selectedHabit == null && data.ranking.isNotEmpty()) RankingCard(data.ranking)
        }

        OutlinedButton(
            onClick = onOpenWeekly,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("open_weekly_overview"),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.stats_weekly_overview))
            Spacer(Modifier.size(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        PremiumHeader()
        if (data.selectedHabit == null) {
            if (data.insights != null) {
                InsightsCard(data.insights)
            } else {
                PremiumLockedCard(
                    title = stringResource(R.string.insights_locked_title),
                    message = stringResource(R.string.insights_locked_message),
                    onUpgrade = { onUpgrade(PremiumFeature.PREMIUM_INSIGHTS) },
                    modifier = Modifier.testTag("insights_locked"),
                )
            }
        } else {
            Text(
                stringResource(R.string.insights_overall_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(data: StatsData) {
    val accent = data.selectedHabit?.let { Color(it.colorArgb).habitAccent(TracklyTheme.extras.isDark) }
        ?: MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProgressRing(
                progress = data.completionRate,
                color = accent,
                strokeWidth = 12.dp,
                modifier = Modifier.size(104.dp),
            ) {
                Text("${(data.completionRate * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.stats_completion_rate),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.stats_rate_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricGrid(data: StatsData) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("✅", data.totalCompletions.toString(), stringResource(R.string.stats_total_completions), Modifier.weight(1f).testTag("stats_total"))
            StatTile("🎯", "${(data.averageProgress * 100).roundToInt()}%", stringResource(R.string.stats_average_completion), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("🔥", data.currentStreak.toString(), stringResource(R.string.details_current_streak), Modifier.weight(1f).testTag("stats_streak"))
            StatTile("🏆", data.bestStreak.toString(), stringResource(R.string.details_best_streak), Modifier.weight(1f).testTag("stats_best"))
        }
    }
}

@Composable
private fun ActivityCard(data: StatsData) {
    val dates = rememberDateFormats()
    val accent = data.selectedHabit?.let { Color(it.colorArgb).habitAccent(TracklyTheme.extras.isDark) }
        ?: MaterialTheme.colorScheme.primary
    val bars = data.buckets.map { bucket ->
        val label = when (data.period) {
            StatsPeriod.WEEK -> dates.weekdayNarrow(bucket.start.dayOfWeek)
            StatsPeriod.MONTH -> dates.mediumDate(bucket.start)
            StatsPeriod.YEAR -> dates.month(java.time.YearMonth.from(bucket.start))
        }
        BarData(
            label = label,
            fraction = bucket.fraction,
            description = stringResource(R.string.chart_bucket_description, label, (bucket.fraction * 100).roundToInt()),
            highlighted = bucket.isCurrent,
            muted = bucket.isFuture,
        )
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(
                stringResource(
                    when (data.period) {
                        StatsPeriod.WEEK -> R.string.stats_weekly_activity
                        StatsPeriod.MONTH -> R.string.stats_monthly_activity
                        StatsPeriod.YEAR -> R.string.stats_yearly_activity
                    },
                ),
            )
            BarChart(
                bars = bars,
                barColor = accent,
                maxBarWidth = if (data.period == StatsPeriod.YEAR) 24.dp else 40.dp,
            )
        }
    }
}

@Composable
private fun RankingCard(ranking: List<HabitRate>) {
    val isDark = TracklyTheme.extras.isDark
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(stringResource(R.string.stats_by_habit))
            ranking.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.habit.icon}  ${item.habit.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${(item.rate * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    }
                    LinearRate(
                        fraction = item.rate,
                        color = Color(item.habit.colorArgb).habitAccent(isDark),
                        height = 8.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumHeader() {
    val extras = TracklyTheme.extras
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.stats_premium_section))
        Row(
            modifier = Modifier
                .background(extras.premiumContainer, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = extras.premium, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.premium_badge), style = MaterialTheme.typography.labelSmall, color = extras.premium)
        }
    }
}

@Composable
private fun InsightsCard(insights: Insights) {
    val dates = rememberDateFormats()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.testTag("insights_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val hasAnything = insights.bestDay != null || insights.topHabit != null || insights.weeklyTrend != null
            if (!hasAnything) {
                Text(
                    stringResource(R.string.insights_not_enough_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            insights.bestDay?.let {
                InsightRow("🌟", stringResource(R.string.insight_best_day), dates.weekdayFull(it))
            }
            insights.weakestDay?.let {
                InsightRow("🌧️", stringResource(R.string.insight_weakest_day), dates.weekdayFull(it))
            }
            insights.topHabit?.let {
                InsightRow(
                    "🏅",
                    stringResource(R.string.insight_top_habit),
                    "${it.habit.icon} ${it.habit.name} · ${(it.rate * 100).roundToInt()}%",
                )
            }
            insights.weeklyTrend?.let { trend ->
                val points = (trend * 100).roundToInt()
                val text = when {
                    points > 0 -> stringResource(R.string.insight_trend_up, points)
                    points < 0 -> stringResource(R.string.insight_trend_down, -points)
                    else -> stringResource(R.string.insight_trend_flat)
                }
                InsightRow(if (points >= 0) "📈" else "📉", stringResource(R.string.insight_trend), text)
            }
            InsightRow("✨", stringResource(R.string.insight_perfect_days), insights.perfectDaysLast30.toString())
        }
    }
}

@Composable
private fun InsightRow(emoji: String, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
