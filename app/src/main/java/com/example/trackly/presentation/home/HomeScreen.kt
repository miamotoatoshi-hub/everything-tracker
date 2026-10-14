@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.trackly.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.presentation.common.unitLabel
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ConfirmDialog
import com.example.trackly.ui.components.EmptyState
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.ProgressRing
import com.example.trackly.ui.components.ValueDialog
import com.example.trackly.ui.components.adaptiveColumns
import com.example.trackly.ui.theme.TracklyTheme
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenHabit: (Long) -> Unit,
    onAddHabit: () -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
    onOpenWeekly: () -> Unit,
    onReorder: () -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(it.habitRepository, it.todayProvider, it.premiumPolicy) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchVisible by viewModel.isSearchVisible.collectAsStateWithLifecycle()
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)

    var valueDialogFor by remember { mutableStateOf<HabitItem?>(null) }
    var deleteFor by remember { mutableStateOf<HabitItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.GoalReached -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(context.getString(R.string.home_goal_reached, event.habitName))
                }
                is HomeEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                HomeEvent.ActionFailed -> snackbar.showSnackbar(context.getString(R.string.error_action_failed))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(onRetry = viewModel::retry)
            is UiState.Content -> {
                val content = current.data
                if (!content.hasAnyHabits) {
                    EmptyState(
                        emoji = "🌱",
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_create_habit),
                        onAction = onAddHabit,
                        modifier = Modifier.testTag("home_empty"),
                    )
                } else {
                    CenteredContent(maxWidth = 960.dp) {
                        HomeList(
                            content = content,
                            searchVisible = searchVisible,
                            query = query,
                            onQueryChange = viewModel::setQuery,
                            onToggleSearch = viewModel::toggleSearch,
                            onSelectFilter = viewModel::selectFilter,
                            onOpenHabit = onOpenHabit,
                            onQuickAdd = viewModel::onQuickAdd,
                            onLongPressAdd = { valueDialogFor = it },
                            onOpenWeekly = onOpenWeekly,
                            onReorder = onReorder,
                            onUnarchive = viewModel::onUnarchive,
                            onDelete = { deleteFor = it },
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (content.canCreateHabit) onAddHabit() else onUpgrade(PremiumFeature.UNLIMITED_HABITS)
                        },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.home_add_habit)) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("add_habit"),
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp, start = 16.dp, end = 16.dp),
        )
    }

    valueDialogFor?.let { item ->
        ValueDialog(
            title = stringResource(R.string.set_value_title, item.habit.name),
            unit = unitLabel(item.habit),
            initialValue = item.value,
            onConfirm = {
                viewModel.onSetValue(item, it)
                valueDialogFor = null
            },
            onDismiss = { valueDialogFor = null },
        )
    }
    deleteFor?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_habit_title),
            message = stringResource(R.string.delete_habit_message, item.habit.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.onDelete(item)
                deleteFor = null
            },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun HomeList(
    content: HomeContent,
    searchVisible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectFilter: (HomeFilter) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onQuickAdd: (HabitItem) -> Unit,
    onLongPressAdd: (HabitItem) -> Unit,
    onOpenWeekly: () -> Unit,
    onReorder: () -> Unit,
    onUnarchive: (HabitItem) -> Unit,
    onDelete: (HabitItem) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val columns = adaptiveColumns()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag("home_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            HomeHeader(
                content = content,
                onToggleSearch = onToggleSearch,
                onReorder = onReorder,
                onOpenWeekly = onOpenWeekly,
            )
        }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(selected = content.filter, onSelect = onSelectFilter)
        }
        if (searchVisible) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                SearchField(query = query, onQueryChange = onQueryChange)
            }
        }

        if (content.filter == HomeFilter.TODAY) {
            val noneToShow = content.todo.isEmpty() && content.done.isEmpty()
            if (noneToShow) {
                item(key = "empty_today", span = { GridItemSpan(maxLineSpan) }) {
                    InlineEmpty(
                        emoji = if (content.isFiltered) "🔍" else "☀️",
                        title = stringResource(
                            if (content.isFiltered) R.string.empty_search_title else R.string.empty_today_title,
                        ),
                        message = stringResource(
                            if (content.isFiltered) R.string.empty_search_message else R.string.empty_today_message,
                        ),
                    )
                }
            }
            if (content.todo.isNotEmpty()) {
                item(key = "todo_label", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(stringResource(R.string.section_todo, content.todo.size))
                }
                items(content.todo, key = { "habit-${it.habit.id}" }) { item ->
                    HabitCardEntry(item, onOpenHabit, onQuickAdd, onLongPressAdd)
                }
            }
            if (content.todo.isEmpty() && content.done.isNotEmpty() && !content.isFiltered) {
                item(key = "all_done", span = { GridItemSpan(maxLineSpan) }) { AllDoneBanner() }
            }
            if (content.done.isNotEmpty()) {
                item(key = "done_label", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(stringResource(R.string.section_done, content.done.size))
                }
                items(content.done, key = { "habit-${it.habit.id}" }) { item ->
                    HabitCardEntry(item, onOpenHabit, onQuickAdd, onLongPressAdd)
                }
            }
        } else if (content.flat.isEmpty()) {
            item(key = "empty_filter", span = { GridItemSpan(maxLineSpan) }) {
                val (emoji, title, message) = emptyFilterTexts(content)
                InlineEmpty(emoji = emoji, title = title, message = message)
            }
        } else if (content.filter == HomeFilter.ARCHIVED) {
            items(content.flat, key = { "habit-${it.habit.id}" }, span = { GridItemSpan(maxLineSpan) }) { item ->
                ArchivedRow(
                    item = item,
                    onRestore = { onUnarchive(item) },
                    onDelete = { onDelete(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            items(content.flat, key = { "habit-${it.habit.id}" }) { item ->
                HabitCardEntry(item, onOpenHabit, onQuickAdd, onLongPressAdd)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.HabitCardEntry(
    item: HabitItem,
    onOpenHabit: (Long) -> Unit,
    onQuickAdd: (HabitItem) -> Unit,
    onLongPressAdd: (HabitItem) -> Unit,
) {
    HabitCard(
        item = item,
        onClick = { onOpenHabit(item.habit.id) },
        onQuickAdd = { onQuickAdd(item) },
        onLongPressAdd = { onLongPressAdd(item) },
        modifier = Modifier.animateItem(),
    )
}

@Composable
private fun emptyFilterTexts(content: HomeContent): Triple<String, String, String> = when {
    content.isFiltered -> Triple(
        "🔍",
        stringResource(R.string.empty_search_title),
        stringResource(R.string.empty_search_message),
    )
    content.filter == HomeFilter.COMPLETED -> Triple(
        "🎯",
        stringResource(R.string.empty_completed_title),
        stringResource(R.string.empty_completed_message),
    )
    content.filter == HomeFilter.INCOMPLETE -> Triple(
        "🎉",
        stringResource(R.string.empty_incomplete_title),
        stringResource(R.string.empty_incomplete_message),
    )
    content.filter == HomeFilter.ARCHIVED -> Triple(
        "🗄️",
        stringResource(R.string.empty_archived_title),
        stringResource(R.string.empty_archived_message),
    )
    else -> Triple(
        "🌱",
        stringResource(R.string.home_empty_title),
        stringResource(R.string.home_empty_message),
    )
}

@Composable
private fun HomeHeader(
    content: HomeContent,
    onToggleSearch: () -> Unit,
    onReorder: () -> Unit,
    onOpenWeekly: () -> Unit,
) {
    val dates = rememberDateFormats()
    val hour = remember { LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dates.fullDate(content.today),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home_date"),
                )
            }
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(48.dp).testTag("search_toggle")) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.home_search))
            }
            if (content.activeHabitCount > 1) {
                IconButton(onClick = onReorder, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.home_reorder))
                }
            }
        }
        ProgressCard(content = content, onOpenWeekly = onOpenWeekly)
    }
}

@Composable
private fun ProgressCard(content: HomeContent, onOpenWeekly: () -> Unit) {
    val percent by animateIntAsState(targetValue = content.progressPercent, label = "percent")
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val ringColor = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("progress_card"),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_today_progress),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        text = if (content.scheduledCount == 0) "–" else "$percent%",
                        style = MaterialTheme.typography.displayMedium,
                        color = onContainer,
                    )
                    Text(
                        text = if (content.scheduledCount == 0) {
                            stringResource(R.string.home_nothing_scheduled)
                        } else {
                            stringResource(R.string.home_progress_summary, content.completedCount, content.scheduledCount)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.85f),
                    )
                }
                ProgressRing(
                    progress = content.progressPercent / 100f,
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.18f),
                    strokeWidth = 12.dp,
                    modifier = Modifier.size(92.dp),
                ) {
                    Text(
                        text = if (content.scheduledCount == 0) "☀️" else "${content.completedCount}/${content.scheduledCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content.topStreak > 0) {
                    StreakChip(streak = content.topStreak)
                } else {
                    Text(
                        text = stringResource(R.string.home_start_streak),
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenWeekly, modifier = Modifier.testTag("open_weekly")) {
                    Text(stringResource(R.string.home_this_week), color = onContainer)
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: HomeFilter, onSelect: (HomeFilter) -> Unit) {
    val labels = mapOf(
        HomeFilter.TODAY to stringResource(R.string.filter_today),
        HomeFilter.ALL to stringResource(R.string.filter_all),
        HomeFilter.COMPLETED to stringResource(R.string.filter_completed),
        HomeFilter.INCOMPLETE to stringResource(R.string.filter_incomplete),
        HomeFilter.ARCHIVED to stringResource(R.string.filter_archived),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("filter_row"),
    ) {
        items(HomeFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(labels.getValue(filter)) },
                modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().testTag("search_field"),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun AllDoneBanner() {
    val extras = TracklyTheme.extras
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = extras.successContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🎉", style = MaterialTheme.typography.headlineSmall)
            Column {
                Text(
                    stringResource(R.string.home_all_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.home_all_done_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InlineEmpty(emoji: String, title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArchivedRow(
    item: HabitItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.habit.icon, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = item.habit.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = onRestore, modifier = Modifier.testTag("restore_${item.habit.id}")) {
                Icon(Icons.Rounded.Unarchive, contentDescription = stringResource(R.string.action_unarchive))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
