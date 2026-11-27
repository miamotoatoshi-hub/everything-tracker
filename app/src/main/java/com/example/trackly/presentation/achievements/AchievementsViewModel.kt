package com.example.trackly.presentation.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.AchievementProgress
import com.example.trackly.domain.logic.Achievements
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class AchievementsContent(
    val items: List<AchievementProgress>,
    val next: AchievementProgress?,
) {
    val unlockedCount: Int get() = items.count { it.unlocked }
}

class AchievementsViewModel(habits: HabitRepository, todayProvider: TodayProvider) : ViewModel() {

    private val source = RetryableState(viewModelScope) {
        combine(habits.observeHabits(), habits.observeEntries(), todayProvider.today) { list, entries, today ->
            val progress = Achievements.progress(Achievements.stats(list, entries, today))
            // Earned badges first, then the ones closest to being earned.
            val ordered = progress.sortedWith(compareBy<AchievementProgress> { !it.unlocked }.thenByDescending { it.fraction })
            AchievementsContent(ordered, Achievements.next(progress))
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<AchievementsContent>> = source.state

    fun retry() = source.retry()
}
