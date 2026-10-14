package com.example.trackly.core.time

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

/**
 * Holds "today" as observable state so every screen rolls over at midnight. It is refreshed when the
 * app resumes and whenever the system reports a date/time/time-zone change.
 */
class TodayProvider(private val clock: Clock) {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun current(): LocalDate = _today.value

    fun refresh() {
        _today.value = LocalDate.now(clock)
    }
}
