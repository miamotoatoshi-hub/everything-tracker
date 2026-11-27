@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.HabitMonthRate
import com.example.trackly.domain.logic.MonthlyReport
import com.example.trackly.presentation.common.AppLinks
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.rememberLocale
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.theme.TracklyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ReportsViewModel(it.habitRepository, it.todayProvider, it.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = rememberLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val content = (state as? UiState.Content<ReportsContent>)?.data
                    if (content != null && !content.report.isEmpty) {
                        IconButton(onClick = { shareReport(context, content.report, locale) }, modifier = Modifier.testTag("reports_share")) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.milestone_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = viewModel::retry)
                is UiState.Content -> CenteredContent(maxWidth = 640.dp) {
                    ReportsBody(current.data, locale, viewModel::previousMonth, viewModel::nextMonth)
                }
            }
        }
    }
}

private fun monthTitle(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

private fun percent(value: Float): Int = (value * 100).roundToInt()

private fun shareReport(context: Context, report: MonthlyReport, locale: Locale) {
    val text = context.getString(
        R.string.reports_share_message,
        monthTitle(report.month, locale),
        percent(report.rate),
        report.perfectDays,
        report.bestStreak,
        AppLinks.playStoreUrl(context.packageName),
    )
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: ActivityNotFoundException) {
        // Nothing can share text: ignore.
    }
}

@Composable
private fun ReportsBody(content: ReportsContent, locale: Locale, onPrevious: () -> Unit, onNext: () -> Unit) {
    val report = content.report
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("reports_body"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrevious, enabled = content.canGoBack, modifier = Modifier.testTag("reports_prev")) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                monthTitle(report.month, locale),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).testTag("reports_month"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNext, enabled = content.canGoForward, modifier = Modifier.testTag("reports_next")) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }

        if (report.isEmpty) {
            Card { Text(stringResource(R.string.reports_empty), style = MaterialTheme.typography.bodyLarge) }
        } else {
            Hero(report)
            Tiles(report)
            TopHabits(report.habits)
            Weekdays(report, content.firstDay, locale)
        }
        YearPixels(content, locale)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Hero(report: MonthlyReport) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${percent(report.rate)}%",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("reports_rate"),
            )
            Text(stringResource(R.string.reports_rate_label), color = MaterialTheme.colorScheme.onPrimaryContainer)
            val previous = report.previousRate
            val delta = if (previous == null) null else percent(report.rate) - percent(previous)
            Text(
                when {
                    delta == null -> stringResource(R.string.reports_no_previous)
                    else -> stringResource(R.string.reports_vs_previous, (if (delta > 0) "+" else "") + "$delta%")
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    delta == null || delta == 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                    delta > 0 -> if (TracklyTheme.extras.isDark) Color(0xFF7EE2A8) else Color(0xFF1B8F4E)
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun Tiles(report: MonthlyReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("✅", report.done.toString(), stringResource(R.string.reports_goals_done), Modifier.weight(1f))
            Tile("✨", report.perfectDays.toString(), stringResource(R.string.reports_perfect_days), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("📅", report.activeDays.toString(), stringResource(R.string.reports_active_days), Modifier.weight(1f))
            Tile(
                report.bestStreakHabit?.icon ?: "🔥",
                report.bestStreak.toString(),
                stringResource(R.string.reports_best_streak),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tile(emoji: String, value: String, label: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 20.sp)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun TopHabits(habits: List<HabitMonthRate>) {
    if (habits.isEmpty()) return
    Card {
        Text(stringResource(R.string.reports_top_habits), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        habits.take(3).forEach { RateRow(it) }
        val weakest = habits.last()
        if (habits.size >= 4 && weakest.rate < 0.6f) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.reports_needs_attention), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            RateRow(weakest)
        }
    }
}

@Composable
private fun RateRow(item: HabitMonthRate) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${item.habit.icon} ${item.habit.name}".trim(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${percent(item.rate)}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Bar(item.rate)
    }
}

@Composable
private fun Bar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Weekdays(report: MonthlyReport, firstDay: DayOfWeek, locale: Locale) {
    if (report.weekdayRates.isEmpty()) return
    val order = (0 until 7).map { firstDay.plus(it.toLong()) }
    Card {
        Text(stringResource(R.string.reports_best_weekday), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        report.bestWeekday?.let { best ->
            Text(
                stringResource(
                    R.string.reports_best_weekday_value,
                    best.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                    percent(report.weekdayRates[best] ?: 0f),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            order.forEach { day ->
                val rate = report.weekdayRates[day]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .weight(1f, fill = false)
                            .fillMaxHeight(((rate ?: 0f) * 0.8f).coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (day == report.bestWeekday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearPixels(content: ReportsContent, locale: Locale) {
    val year = content.today.year
    val firstDay = content.firstDay
    val start = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(firstDay))
    val end = LocalDate.of(year, 12, 31)
    val weeks = (ChronoUnit.DAYS.between(start, end) / 7 + 1).toInt()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val low = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val high = MaterialTheme.colorScheme.primary
    Card {
        Text(
            stringResource(R.string.reports_year_pixels) + " · $year",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(weeks / 7f).testTag("year_pixels")) {
            val cell = size.width / weeks
            val gap = cell * 0.18f
            for (week in 0 until weeks) {
                for (row in 0 until 7) {
                    val date = start.plusDays(week * 7L + row)
                    if (date.year != year || date.isAfter(content.today)) continue
                    val rate = content.pixels[date]
                    val color = if (rate == null) empty else lerp(low, high, rate)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(week * cell + gap / 2, row * cell + gap / 2),
                        size = Size(cell - gap, cell - gap),
                        cornerRadius = CornerRadius(cell * 0.22f),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reports_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.33f, 0.66f, 1f).forEach { level ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(lerp(low, high, level)))
            }
            Text(stringResource(R.string.reports_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
