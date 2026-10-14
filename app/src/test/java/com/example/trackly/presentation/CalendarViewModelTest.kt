package com.example.trackly.presentation

import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.fakes.FakeHabitRepository
import com.example.trackly.fakes.FakeSettingsRepository
import com.example.trackly.fakes.Fixtures
import com.example.trackly.presentation.calendar.CalendarData
import com.example.trackly.presentation.calendar.CalendarEvent
import com.example.trackly.presentation.calendar.CalendarViewModel
import com.example.trackly.presentation.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CalendarViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val today = Fixtures.TODAY
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

    private fun repo() = FakeHabitRepository(listOf(Fixtures.habit(id = 1, name = "Water"))).also {
        it.entries.value = listOf(
            Fixtures.done(1, today.minusDays(2)),
            Fixtures.entry(1, today.minusDays(1), 3.0), // partial
        )
    }

    private fun viewModel(premium: Boolean) = mainDispatcher.track(
        CalendarViewModel(
            habits = repo(),
            todayProvider = TodayProvider(clock),
            settings = FakeSettingsRepository(AppSettings(firstDayOfWeek = FirstDayOfWeekSetting.MONDAY)),
            policy = MutableStateFlow(PremiumPolicy(premium)),
        ),
    )

    private suspend fun CalendarViewModel.data(predicate: (CalendarData) -> Boolean = { true }): CalendarData =
        (uiState.first { it is UiState.Content && predicate(it.data) } as UiState.Content).data

    @Test
    fun `month grid exposes the state of each day`() = runTest {
        val data = viewModel(premium = false).data()

        assertEquals(YearMonth.of(2026, 10), data.month)
        assertEquals(DayState.COMPLETED, data.dayStates[today.minusDays(2)])
        assertEquals(DayState.PARTIAL, data.dayStates[today.minusDays(1)])
        assertEquals(DayState.PENDING, data.dayStates[today])
        assertEquals(DayState.FUTURE, data.dayStates[today.plusDays(1)])
        assertFalse(data.canGoNext)
    }

    @Test
    fun `selecting a day lists the habits for that day`() = runTest {
        val vm = viewModel(premium = false)
        val day: LocalDate = today.minusDays(2)
        vm.selectDate(day)
        val data = vm.data { it.selectedDate == day }
        assertEquals(1, data.dayRows.size)
        assertEquals(DayState.COMPLETED, data.dayRows.single().state)

        vm.selectDate(day) // tapping again deselects
        assertEquals(null, vm.data { it.selectedDate == null }.selectedDate)
    }

    @Test
    fun `free plan can browse two months back and is then offered Premium`() = runTest {
        val vm = viewModel(premium = false)
        val events = mutableListOf<CalendarEvent>()
        val job = launch(Dispatchers.Unconfined) { vm.events.collect { events.add(it) } }

        vm.previousMonth() // September
        vm.previousMonth() // August: the oldest free month
        assertEquals(YearMonth.of(2026, 8), vm.data { it.month == YearMonth.of(2026, 8) }.month)
        assertTrue(vm.data { it.month == YearMonth.of(2026, 8) }.previousLocked)

        vm.previousMonth() // July: locked
        job.cancel()
        assertEquals(CalendarEvent.RequiresPremium(PremiumFeature.ADVANCED_CALENDAR), events.single())
        assertEquals(YearMonth.of(2026, 8), vm.data().month)
    }

    @Test
    fun `premium can go back as far as it wants`() = runTest {
        val vm = viewModel(premium = true)
        repeat(12) { vm.previousMonth() }
        assertEquals(YearMonth.of(2025, 10), vm.data { it.month == YearMonth.of(2025, 10) }.month)
    }

    @Test
    fun `editing a day stores the value`() = runTest {
        val vm = viewModel(premium = false)
        vm.setValue(1, today.minusDays(3), 8.0)
        val data = vm.data { it.dayStates[today.minusDays(3)] == DayState.COMPLETED }
        assertEquals(DayState.COMPLETED, data.dayStates[today.minusDays(3)])
    }
}
