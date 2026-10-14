package com.example.trackly.presentation.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class ReorderViewModel(private val habits: HabitRepository) : ViewModel() {

    private val source = RetryableState(viewModelScope) {
        habits.observeHabits().map { list -> list.filter { !it.isArchived } }
    }

    val uiState: StateFlow<UiState<List<Habit>>> = source.state

    fun retry() = source.retry()

    /** Moves the habit at [index] one step up (-1) or down (+1) and stores the new order. */
    fun move(current: List<Habit>, index: Int, direction: Int) {
        val target = index + direction
        if (index !in current.indices || target !in current.indices) return
        val reordered = current.toMutableList().apply {
            val moved = removeAt(index)
            add(target, moved)
        }
        launchSafely {
            habits.updateOrder(reordered.map { it.id })
        }
    }
}
