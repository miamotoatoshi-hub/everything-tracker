package com.example.trackly.presentation

import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.model.FreeTier
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.domain.usecase.SaveHabitUseCase
import com.example.trackly.fakes.FakeHabitRepository
import com.example.trackly.fakes.FakeSettingsRepository
import com.example.trackly.presentation.habit.HabitFormEvent
import com.example.trackly.presentation.habit.HabitFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class HabitFormViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)
    private val repo = FakeHabitRepository()

    private fun viewModel(premium: Boolean = false, habitId: Long = 0) = HabitFormViewModel(
        habitId = habitId,
        habits = repo,
        saveHabit = SaveHabitUseCase(repo),
        settings = FakeSettingsRepository(),
        policy = MutableStateFlow(PremiumPolicy(premium)),
        todayProvider = TodayProvider(clock),
    )

    @Test
    fun `saving without a name reports a validation error and stores nothing`() = runTest {
        val vm = viewModel()
        vm.save()
        assertTrue(HabitFormError.NAME_EMPTY in vm.state.value.errors)
        assertEquals(0, repo.countNonArchived())
    }

    @Test
    fun `a valid boolean habit is saved`() = runTest {
        val vm = viewModel()
        val events = mutableListOf<HabitFormEvent>()
        val job = launch(Dispatchers.Unconfined) { vm.events.collect { events.add(it) } }
        vm.onNameChange("Take vitamins")
        vm.save()
        job.cancel()
        assertEquals(1, repo.countNonArchived())
        assertTrue(events.single() is HabitFormEvent.Saved)
    }

    @Test
    fun `changing the type resets goal unit and step`() = runTest {
        val vm = viewModel()
        vm.onTypeChange(HabitType.COUNTER)
        assertEquals("8", vm.state.value.goalText)
        vm.onTypeChange(HabitType.DURATION)
        assertEquals("30", vm.state.value.goalText)
        assertEquals("5", vm.state.value.stepText)
    }

    @Test
    fun `specific days preselects today and an empty selection is rejected`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Gym")
        vm.onScheduleTypeChange(ScheduleType.SPECIFIC_DAYS)
        assertEquals(1, vm.state.value.days.size) // today (Wednesday)
        vm.onToggleDay(vm.state.value.days.single())
        vm.save()
        assertTrue(HabitFormError.NO_DAYS_SELECTED in vm.state.value.errors)
    }

    @Test
    fun `free plan cannot pick a premium colour but premium can`() = runTest {
        val free = viewModel(premium = false)
        val events = mutableListOf<HabitFormEvent>()
        val job = launch(Dispatchers.Unconfined) { free.events.collect { events.add(it) } }
        free.onColorChange(HabitColors.PREMIUM.first())
        job.cancel()
        assertEquals(HabitColors.DEFAULT, free.state.value.colorArgb)
        assertEquals(HabitFormEvent.RequiresPremium(PremiumFeature.CUSTOM_COLORS), events.single())

        val premium = viewModel(premium = true)
        premium.onColorChange(HabitColors.PREMIUM.first())
        assertEquals(HabitColors.PREMIUM.first(), premium.state.value.colorArgb)
    }

    @Test
    fun `free plan reminder limit shows the upsell`() = runTest {
        val vm = viewModel(premium = false)
        repeat(FreeTier.MAX_REMINDERS_PER_HABIT) { vm.addReminder(LocalTime.of(8 + it, 0), "", false) }
        assertEquals(FreeTier.MAX_REMINDERS_PER_HABIT, vm.state.value.reminders.size)
        assertFalse(vm.canAddReminder())
        vm.addReminder(LocalTime.of(20, 0), "", false)
        assertEquals(FreeTier.MAX_REMINDERS_PER_HABIT, vm.state.value.reminders.size)
    }

    @Test
    fun `dirty tracking`() = runTest {
        val vm = viewModel()
        assertFalse(vm.isDirty)
        vm.onNameChange("Read")
        assertTrue(vm.isDirty)
    }
}
