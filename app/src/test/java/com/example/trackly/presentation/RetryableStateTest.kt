package com.example.trackly.presentation

import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryableStateTest {

    @Test
    fun `coming back to a screen keeps its content instead of flashing Loading`() = runTest(UnconfinedTestDispatcher()) {
        val source = MutableStateFlow(1)
        val state = RetryableState(backgroundScope) { source }.state
        val seen = mutableListOf<UiState<Int>>()

        val first = backgroundScope.launch { state.collect { seen += it } }
        assertEquals(UiState.Content(1), state.first { it is UiState.Content })
        first.cancel()
        advanceTimeBy(10_000) // the screen was hidden long enough for the upstream to stop

        seen.clear()
        val second = backgroundScope.launch { state.collect { seen += it } }
        source.value = 2
        second.cancel()
        assertTrue("no Loading after a restart: $seen", seen.none { it is UiState.Loading })
        assertEquals(UiState.Content(2), seen.last())
    }

    @Test
    fun `retry shows Loading again`() = runTest(UnconfinedTestDispatcher()) {
        var fail = true
        val retryable = RetryableState(backgroundScope) {
            kotlinx.coroutines.flow.flow {
                if (fail) error("boom")
                emit(42)
            }
        }
        val seen = mutableListOf<UiState<Int>>()
        backgroundScope.launch { retryable.state.collect { seen += it } }
        assertTrue(seen.last() is UiState.Error)
        fail = false
        retryable.retry()
        assertTrue(UiState.Loading in seen)
        assertEquals(UiState.Content(42), seen.last())
    }
}
