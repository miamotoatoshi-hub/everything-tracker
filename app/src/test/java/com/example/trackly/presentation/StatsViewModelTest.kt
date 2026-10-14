package com.example.trackly.presentation

import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.StatsPeriod
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.fakes.FakeHabitRepository
import com.example.trackly.fakes.FakeSettingsRepository
import com.example.trackly.fakes.Fixtures
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.stats.StatsData
import com.example.trackly.presentation.stats.StatsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StatsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val today = Fixtures.TODAY // Wednesday
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

    private fun repo(): FakeHabitRepository {
        val repo = FakeHabitRepository(
            listOf(Fixtures.habit(id = 1, name = "Water"), Fixtures.habit(id = 2, name = "Yoga")),
        )
        // Water: Mon, Tue and today done. Yoga: nothing.
        repo.entries.value = listOf(
            Fixtures.done(1, today.minusDays(2)),
            Fixtures.done(1, today.minusDays(1)),
            Fixtures.done(1, today),
        )
        return repo
    }

    private fun viewModel(premium: Boolean) = mainDispatcher.track(
        StatsViewModel(
            habits = repo(),
            todayProvider = TodayProvider(clock),
            settings = FakeSettingsRepository(AppSettings(firstDayOfWeek = FirstDayOfWeekSetting.MONDAY)),
            policy = MutableStateFlow(PremiumPolicy(premium)),
        ),
    )

    private suspend fun StatsViewModel.data(predicate: (StatsData) -> Boolean = { true }): StatsData =
        (uiState.first { it is UiState.Content && predicate(it.data) } as UiState.Content).data

    @Test
    fun `overall week statistics count every scheduled habit`() = runTest {
        val data = viewModel(premium = false).data()

        assertEquals(StatsPeriod.WEEK, data.period)
        assertFalse(data.locked)
        // Mon + Tue: 2 habits each (4 slots) + today's completed Water = 5 eligible, 3 completed.
        assertEquals(0.6f, data.completionRate, 0.0001f)
        assertEquals(3, data.totalCompletions)
        assertEquals(3, data.currentStreak)
        assertEquals(7, data.buckets.size)
        assertEquals(2, data.ranking.size)
        assertEquals("Water", data.ranking.first().habit.name)
    }

    @Test
    fun `free plan sees month and year as locked, premium does not`() = runTest {
        val free = viewModel(premium = false)
        free.selectPeriod(StatsPeriod.MONTH)
        assertTrue(free.data { it.period == StatsPeriod.MONTH }.locked)
        free.selectPeriod(StatsPeriod.YEAR)
        assertTrue(free.data { it.period == StatsPeriod.YEAR }.locked)

        val premium = viewModel(premium = true)
        premium.selectPeriod(StatsPeriod.YEAR)
        val data = premium.data { it.period == StatsPeriod.YEAR }
        assertFalse(data.locked)
        assertEquals(12, data.buckets.size)
    }

    @Test
    fun `per habit scope reports that habit only`() = runTest {
        val vm = viewModel(premium = false)
        vm.selectHabit(1)
        val data = vm.data { it.selectedHabit != null }
        assertEquals(1L, data.selectedHabit?.id)
        assertTrue(data.ranking.isEmpty())
        assertEquals(1f, data.completionRate, 0.0001f)
        assertEquals(3, data.currentStreak)
    }

    @Test
    fun `insights are a premium feature`() = runTest {
        assertNull(viewModel(premium = false).data().insights)
        assertNotNull(viewModel(premium = true).data().insights)
    }

    @Test
    fun `month buckets are weeks and the current one is highlighted`() = runTest {
        val vm = viewModel(premium = true)
        vm.selectPeriod(StatsPeriod.MONTH)
        val data = vm.data { it.period == StatsPeriod.MONTH }
        assertTrue(data.buckets.size in 5..6)
        assertEquals(1, data.buckets.count { it.isCurrent })
    }
}
