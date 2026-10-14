package com.example.trackly.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class HomeFilter { TODAY, ALL, COMPLETED, INCOMPLETE, ARCHIVED }

/** A habit together with today's progress. */
data class HabitItem(
    val habit: Habit,
    val value: Double,
    val progress: Float,
    val completed: Boolean,
    val scheduledToday: Boolean,
    val streak: Int,
)

data class HomeSnapshot(
    val today: LocalDate,
    val active: List<HabitItem>,
    val archived: List<HabitItem>,
)

data class HomeContent(
    val today: LocalDate,
    val filter: HomeFilter,
    val query: String,
    /** Habits still to do today (TODAY filter only). */
    val todo: List<HabitItem>,
    /** Habits finished today (TODAY filter only). */
    val done: List<HabitItem>,
    /** Flat list used by every other filter. */
    val flat: List<HabitItem>,
    val scheduledCount: Int,
    val completedCount: Int,
    val progressPercent: Int,
    val topStreak: Int,
    val activeHabitCount: Int,
    val archivedCount: Int,
    val canCreateHabit: Boolean,
) {
    val hasAnyHabits: Boolean get() = activeHabitCount + archivedCount > 0
    val isFiltered: Boolean get() = query.isNotBlank()
}

sealed interface HomeEvent {
    data class GoalReached(val habitName: String) : HomeEvent
    data class RequiresPremium(val feature: PremiumFeature) : HomeEvent
    data object ActionFailed : HomeEvent
}

class HomeViewModel(
    private val habits: HabitRepository,
    private val todayProvider: TodayProvider,
    private val policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val filter = MutableStateFlow(HomeFilter.TODAY)
    private val query = MutableStateFlow("")
    private val searchVisible = MutableStateFlow(false)

    val isSearchVisible: StateFlow<Boolean> = searchVisible.asStateFlow()
    val currentQuery: StateFlow<String> = query.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val snapshot = RetryableState(viewModelScope) {
        combine(habits.observeHabits(), habits.observeEntries(), todayProvider.today) { list, entries, today ->
            buildSnapshot(list, entries, today)
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<HomeContent>> =
        combine(snapshot.state, filter, query, policy) { state, selectedFilter, text, currentPolicy ->
            val result: UiState<HomeContent> = when (state) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> state
                is UiState.Content -> UiState.Content(buildContent(state.data, selectedFilter, text, currentPolicy))
            }
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun selectFilter(value: HomeFilter) {
        filter.value = value
    }

    fun setQuery(text: String) {
        query.value = text.take(60)
    }

    fun toggleSearch() {
        val next = !searchVisible.value
        searchVisible.value = next
        if (!next) query.value = ""
    }

    fun retry() = snapshot.retry()

    /** The "+" / check button of a card. */
    fun onQuickAdd(item: HabitItem) {
        launchSafely(onError = { _events.emit(HomeEvent.ActionFailed) }) {
            val habit = item.habit
            val today = todayProvider.current()
            val result = if (habit.type == HabitType.BOOLEAN) {
                habits.setEntryValue(habit.id, today, if (item.completed) 0.0 else 1.0)
            } else {
                habits.addToEntry(habit.id, today, habit.stepValue)
            }
            if (!item.completed && result?.completed == true) {
                _events.emit(HomeEvent.GoalReached(habit.name))
            }
        }
    }

    /** Long-press dialog: set an exact value for today. */
    fun onSetValue(item: HabitItem, value: Double) {
        launchSafely(onError = { _events.emit(HomeEvent.ActionFailed) }) {
            val result = habits.setEntryValue(item.habit.id, todayProvider.current(), value)
            if (!item.completed && result?.completed == true) {
                _events.emit(HomeEvent.GoalReached(item.habit.name))
            }
        }
    }

    fun onUnarchive(item: HabitItem) {
        launchSafely(onError = { _events.emit(HomeEvent.ActionFailed) }) {
            if (!policy.value.canCreateHabit(habits.countNonArchived())) {
                _events.emit(HomeEvent.RequiresPremium(PremiumFeature.UNLIMITED_HABITS))
            } else {
                habits.setArchived(item.habit.id, false)
            }
        }
    }

    fun onDelete(item: HabitItem) {
        launchSafely(onError = { _events.emit(HomeEvent.ActionFailed) }) {
            habits.deleteHabit(item.habit.id)
        }
    }

    private fun buildSnapshot(list: List<Habit>, entries: List<HabitEntry>, today: LocalDate): HomeSnapshot {
        val byHabit = entries.groupBy { it.habitId }
        val items = list.map { habit ->
            val own = byHabit[habit.id].orEmpty()
            val todayEntry = own.firstOrNull { it.date == today }
            val value = todayEntry?.value ?: 0.0
            HabitItem(
                habit = habit,
                value = value,
                progress = GoalProgress.fraction(value, habit.goalValue),
                completed = todayEntry?.completed == true,
                scheduledToday = habit.isActiveOn(today),
                streak = StreakCalculator.compute(habit, own, today).current,
            )
        }
        return HomeSnapshot(today, items.filter { !it.habit.isArchived }, items.filter { it.habit.isArchived })
    }

    private fun buildContent(
        snapshot: HomeSnapshot,
        filter: HomeFilter,
        query: String,
        policy: PremiumPolicy,
    ): HomeContent {
        val scheduled = snapshot.active.filter { it.scheduledToday }
        val completed = scheduled.count { it.completed }
        val percent = if (scheduled.isEmpty()) 0 else completed * 100 / scheduled.size

        fun List<HabitItem>.matching(): List<HabitItem> =
            if (query.isBlank()) this else filter { it.habit.name.contains(query.trim(), ignoreCase = true) }

        val todayItems = scheduled.matching()
        return HomeContent(
            today = snapshot.today,
            filter = filter,
            query = query,
            todo = if (filter == HomeFilter.TODAY) todayItems.filter { !it.completed } else emptyList(),
            done = if (filter == HomeFilter.TODAY) todayItems.filter { it.completed } else emptyList(),
            flat = when (filter) {
                HomeFilter.TODAY -> emptyList()
                HomeFilter.ALL -> snapshot.active.matching()
                HomeFilter.COMPLETED -> todayItems.filter { it.completed }
                HomeFilter.INCOMPLETE -> todayItems.filter { !it.completed }
                HomeFilter.ARCHIVED -> snapshot.archived.matching()
            },
            scheduledCount = scheduled.size,
            completedCount = completed,
            progressPercent = percent,
            topStreak = snapshot.active.maxOfOrNull { it.streak } ?: 0,
            activeHabitCount = snapshot.active.size,
            archivedCount = snapshot.archived.size,
            canCreateHabit = policy.canCreateHabit(snapshot.active.size),
        )
    }
}
