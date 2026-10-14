package com.example.trackly.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main so ViewModels (viewModelScope) can run in plain JVM tests.
 *
 * ViewModels passed to [track] have their scope cancelled *and joined* at the end of the test:
 * the ViewModels use `flowOn(Dispatchers.Default)`, whose producer finishes on a background thread and
 * then hands its completion back to Main. Main is deliberately never reset (the next test simply
 * installs its own test dispatcher), so a straggler can never hit "Dispatchers.Main is missing" and
 * surface as an unrelated failure in the next test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    private val tracked = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T {
        tracked += viewModel
        return viewModel
    }

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) {
        val scopes = tracked.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        tracked.clear()
        runBlocking {
            withTimeout(10_000) { scopes.forEach { it.cancelAndJoin() } }
        }
    }
}
