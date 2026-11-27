package com.example.trackly.presentation.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.FocusTimer
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.repository.HabitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

data class FocusState(
    val habit: Habit? = null,
    val totalSeconds: Int = FocusTimer.DEFAULT_MINUTES * 60,
    val remainingSeconds: Int = FocusTimer.DEFAULT_MINUTES * 60,
    val running: Boolean = false,
    /** Minutes written to today's entry once the session ended; null while it is going. */
    val loggedMinutes: Int? = null,
) {
    val started: Boolean get() = running || remainingSeconds < totalSeconds
    val finished: Boolean get() = loggedMinutes != null
    val progress: Float get() = if (totalSeconds == 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds
}

/** A countdown measured against the monotonic clock, so it stays right while the screen is off. */
class FocusTimerViewModel(
    private val habitId: Long,
    private val habits: HabitRepository,
    private val todayProvider: TodayProvider,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var endAt = 0L
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val habit = habits.observeHabit(habitId).first() ?: return@launch
            val today = habits.getEntry(habitId, todayProvider.current())?.value ?: 0.0
            val seconds = FocusTimer.suggestedMinutes(habit, today) * 60
            _state.update { it.copy(habit = habit, totalSeconds = seconds, remainingSeconds = seconds) }
        }
    }

    fun setMinutes(minutes: Int) {
        if (_state.value.started) return
        val seconds = minutes.coerceIn(1, FocusTimer.MAX_MINUTES) * 60
        _state.update { it.copy(totalSeconds = seconds, remainingSeconds = seconds) }
    }

    fun start() {
        val current = _state.value
        if (current.running || current.finished || current.remainingSeconds <= 0) return
        endAt = now() + current.remainingSeconds * 1000L
        _state.update { it.copy(running = true) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val left = ceil((endAt - now()) / 1000.0).toInt().coerceAtLeast(0)
                _state.update { it.copy(remainingSeconds = left) }
                if (left == 0) {
                    complete(_state.value.totalSeconds)
                    break
                }
                delay(TICK_MILLIS)
            }
        }
    }

    fun pause() {
        ticker?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun addFiveMinutes() {
        if (_state.value.finished) return
        endAt += 5 * 60_000L
        _state.update { it.copy(totalSeconds = it.totalSeconds + 300, remainingSeconds = it.remainingSeconds + 300) }
    }

    /** Ends early and logs the whole minutes spent so far. */
    fun finishNow() {
        if (_state.value.finished) return
        ticker?.cancel()
        val current = _state.value
        viewModelScope.launch { complete(current.totalSeconds - current.remainingSeconds) }
    }

    private suspend fun complete(elapsedSeconds: Int) {
        val habit = _state.value.habit
        val minutes = elapsedSeconds / 60
        if (habit != null && minutes > 0) {
            habits.addToEntry(habitId, todayProvider.current(), FocusTimer.loggedValue(minutes, habit.unit))
        }
        _state.update { it.copy(running = false, loggedMinutes = minutes) }
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
