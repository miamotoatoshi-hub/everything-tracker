package com.example.trackly.presentation.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Every screen is in exactly one of these states: never a blank page. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Error(val cause: Throwable? = null) : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>
}

/**
 * Turns a flow-producing function into a [UiState] with loading, error and retry handling.
 * A failing flow (e.g. a database error) yields [UiState.Error] instead of crashing.
 */
class RetryableState<T>(scope: CoroutineScope, source: () -> Flow<T>) {
    private val trigger = MutableStateFlow(0)

    /** The attempt whose content is on screen; a restart of the same attempt keeps showing it (no flicker). */
    @Volatile private var shownAttempt = -1

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<T>> = trigger
        .flatMapLatest { attempt ->
            source()
                .map<T, UiState<T>> {
                    shownAttempt = attempt
                    UiState.Content(it)
                }
                // Loading only for the first load and for a retry — not when collection restarts after the
                // screen was in the background (that would flash a spinner and lose the scroll position).
                .onStart { if (attempt != shownAttempt) emit(UiState.Loading) }
                .catch { error -> emit(UiState.Error(error)) }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        trigger.value = trigger.value + 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
