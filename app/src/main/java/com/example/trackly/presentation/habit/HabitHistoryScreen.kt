@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.habit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.DayDot
import com.example.trackly.ui.components.EmptyState
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.PremiumLockedCard
import com.example.trackly.ui.components.ValueDialog
import com.example.trackly.ui.components.dayStateDescription
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HabitHistoryScreen(
    habitId: Long,
    onBack: () -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel(key = "habit_history_$habitId") {
        HabitHistoryViewModel(habitId, it.habitRepository, it.todayProvider, it.premiumPolicy)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = (state as? UiState.Content)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title, content?.habit?.name.orEmpty())) },
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
                    if (data == null) {
                        ErrorState(
                            title = stringResource(R.string.habit_not_found_title),
                            message = stringResource(R.string.habit_not_found_message),
                        )
                    } else if (data.rows.isEmpty() && data.hiddenCount == 0) {
                        EmptyState(
                            emoji = "🗒️",
                            title = stringResource(R.string.history_empty_title),
                            message = stringResource(R.string.history_empty_message),
                        )
                    } else {
                        CenteredContent(maxWidth = 640.dp) {
                            HistoryList(data, viewModel, onUpgrade)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface HistoryItem {
    data class Header(val month: YearMonth) : HistoryItem
    data class Day(val row: HistoryRow) : HistoryItem
}

@Composable
private fun HistoryList(data: HistoryData, viewModel: HabitHistoryViewModel, onUpgrade: (PremiumFeature) -> Unit) {
    val habit = data.habit
    val dates = rememberDateFormats()
    val locale = rememberLocale()
    val accent = Color(habit.colorArgb).habitAccent(TracklyTheme.extras.isDark)
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    val unit = unitLabel(habit)

    val items = buildList {
        var lastMonth: YearMonth? = null
        for (row in data.rows) {
            val month = YearMonth.from(row.date)
            if (month != lastMonth) {
                add(HistoryItem.Header(month))
                lastMonth = month
            }
            add(HistoryItem.Day(row))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("history_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { item ->
            when (item) {
                is HistoryItem.Header -> "h-${item.month}"
                is HistoryItem.Day -> "d-${item.row.date}"
            }
        }) { item ->
            when (item) {
                is HistoryItem.Header -> Text(
                    text = dates.monthYear(item.month),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
                is HistoryItem.Day -> {
                    val row = item.row
                    val stateText = dayStateDescription(row.state)
                    val editable = !row.date.isAfter(data.today)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = editable, onClickLabel = stringResource(R.string.action_edit)) {
                                if (habit.type == HabitType.BOOLEAN) {
                                    viewModel.setValue(row.date, if (row.state == DayState.COMPLETED) 0.0 else 1.0)
                                } else {
                                    editing = row.date
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            DayDot(state = row.state, color = accent, size = 26.dp)
                            Text(
                                text = dates.fullDate(row.date),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (habit.type == HabitType.BOOLEAN) {
                                if (row.state == DayState.COMPLETED) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = stateText,
                                        tint = TracklyTheme.extras.success,
                                    )
                                } else {
                                    Text(stateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text(
                                    text = "${formatValue(row.value, locale)} / ${formatValue(row.goal, locale)}" +
                                        if (row.state == DayState.COMPLETED) " ✓" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (row.state == DayState.COMPLETED) TracklyTheme.extras.success else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (data.hiddenCount > 0) {
            item(key = "locked") {
                PremiumLockedCard(
                    title = stringResource(R.string.history_locked_title),
                    message = stringResource(R.string.history_locked_message, data.hiddenCount),
                    onUpgrade = { onUpgrade(HabitHistoryViewModel.lockedFeature) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    editing?.let { date ->
        val row = data.rows.firstOrNull { it.date == date }
        ValueDialog(
            title = stringResource(R.string.set_value_for_date, dates.mediumDate(date)),
            unit = unit,
            initialValue = row?.value ?: 0.0,
            onConfirm = {
                viewModel.setValue(date, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
