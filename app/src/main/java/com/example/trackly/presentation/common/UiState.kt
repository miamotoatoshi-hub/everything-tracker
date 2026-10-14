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

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState<T>> = trigger
        .flatMapLatest {
            source()
                .map<T, UiState<T>> { UiState.Content(it) }
                .onStart { emit(UiState.Loading) }
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
