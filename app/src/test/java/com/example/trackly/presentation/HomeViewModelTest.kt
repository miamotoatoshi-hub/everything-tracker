package com.example.trackly.presentation

import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.FakeHabitRepository
import com.example.trackly.fakes.Fixtures
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.home.HabitItem
import com.example.trackly.presentation.home.HomeContent
import com.example.trackly.presentation.home.HomeEvent
import com.example.trackly.presentation.home.HomeFilter
import com.example.trackly.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val today = Fixtures.TODAY
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(repo: FakeHabitRepository, premium: Boolean = false) =
        mainDispatcher.track(HomeViewModel(repo, TodayProvider(clock), MutableStateFlow(PremiumPolicy(premium))))

    private suspend fun HomeViewModel.content(): HomeContent =
        (uiState.first { it is UiState.Content } as UiState.Content).data

    private suspend fun HomeViewModel.contentWhere(predicate: (HomeContent) -> Boolean): HomeContent =
        (uiState.first { it is UiState.Content && predicate(it.data) } as UiState.Content).data

    @Test
    fun `progress counts only habits scheduled today`() = runTest {
        val water = Fixtures.habit(id = 1)
        val vitamins = Fixtures.habit(id = 2, name = "Vitamins", type = HabitType.BOOLEAN, goal = 1.0, unit = HabitUnit.TIMES)
        val weekendOnly = Fixtures.habit(id = 3, name = "Hike", schedule = HabitSchedule(ScheduleType.WEEKENDS))
        val repo = FakeHabitRepository(listOf(water, vitamins, weekendOnly))
        repo.setEntryValue(2, today, 1.0)

        val content = viewModel(repo).content()

        assertEquals(2, content.scheduledCount) // the weekend habit is not scheduled on a Wednesday
        assertEquals(1, content.completedCount)
        assertEquals(50, content.progressPercent)
        assertEquals(listOf(1L), content.todo.map { it.habit.id })
        assertEquals(listOf(2L), content.done.map { it.habit.id })
    }

    @Test
    fun `quick add increments a counter and completes at the goal`() = runTest {
        val repo = FakeHabitRepository(listOf(Fixtures.habit(id = 1, goal = 2.0)))
        val vm = viewModel(repo)
        val item: HabitItem = vm.content().todo.single()

        vm.onQuickAdd(item)
        assertEquals(1.0, repo.getEntry(1, today)?.value ?: 0.0, 0.0)
        assertTrue(repo.getEntry(1, today)?.completed != true)

        val updated = vm.contentWhere { it.todo.isNotEmpty() && it.todo.single().value == 1.0 }.todo.single()
        vm.onQuickAdd(updated)
        assertTrue(repo.getEntry(1, today)?.completed == true)
    }

    @Test
    fun `boolean quick action toggles`() = runTest {
        val repo = FakeHabitRepository(
            listOf(Fixtures.habit(id = 1, type = HabitType.BOOLEAN, goal = 1.0, unit = HabitUnit.TIMES)),
        )
        val vm = viewModel(repo)
        vm.onQuickAdd(vm.content().todo.single())
        assertTrue(repo.getEntry(1, today)?.completed == true)

        val done = vm.contentWhere { it.done.isNotEmpty() }.done.single()
        vm.onQuickAdd(done)
        assertEquals(null, repo.getEntry(1, today))
    }

    @Test
    fun `filters and search`() = runTest {
        val repo = FakeHabitRepository(
            listOf(
                Fixtures.habit(id = 1, name = "Water"),
                Fixtures.habit(id = 2, name = "Yoga"),
                Fixtures.habit(id = 3, name = "Old", archived = true),
            ),
        )
        repo.setEntryValue(2, today, 8.0)
        val vm = viewModel(repo)

        vm.selectFilter(HomeFilter.COMPLETED)
        assertEquals(listOf("Yoga"), vm.contentWhere { it.filter == HomeFilter.COMPLETED }.flat.map { it.habit.name })

        vm.selectFilter(HomeFilter.INCOMPLETE)
        assertEquals(listOf("Water"), vm.contentWhere { it.filter == HomeFilter.INCOMPLETE }.flat.map { it.habit.name })

        vm.selectFilter(HomeFilter.ARCHIVED)
        assertEquals(listOf("Old"), vm.contentWhere { it.filter == HomeFilter.ARCHIVED }.flat.map { it.habit.name })

        vm.selectFilter(HomeFilter.ALL)
        vm.setQuery("yo")
        assertEquals(listOf("Yoga"), vm.contentWhere { it.filter == HomeFilter.ALL && it.query == "yo" }.flat.map { it.habit.name })
    }

    @Test
    fun `free plan cannot create beyond the limit and restoring an archived habit hits it`() = runTest {
        val habits = (1..5).map { Fixtures.habit(id = it.toLong(), name = "H$it") } +
            Fixtures.habit(id = 6, name = "Archived", archived = true)
        val repo = FakeHabitRepository(habits)

        val free = viewModel(repo, premium = false)
        assertEquals(false, free.content().canCreateHabit)

        val premium = viewModel(repo, premium = true)
        assertEquals(true, premium.content().canCreateHabit)

        val events = mutableListOf<HomeEvent>()
        val job = launch(Dispatchers.Unconfined) { free.events.collect { events.add(it) } }
        free.selectFilter(HomeFilter.ARCHIVED)
        free.onUnarchive(free.contentWhere { it.filter == HomeFilter.ARCHIVED }.flat.single())
        job.cancel()
        assertTrue(events.any { it is HomeEvent.RequiresPremium && it.feature == PremiumFeature.UNLIMITED_HABITS })
        assertTrue(repo.getHabit(6)?.isArchived == true)
    }
}
