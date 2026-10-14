package com.example.trackly.domain

import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.model.FreeTier
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.usecase.SaveHabitResult
import com.example.trackly.domain.usecase.SaveHabitUseCase
import com.example.trackly.fakes.FakeHabitRepository
import com.example.trackly.fakes.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SaveHabitUseCaseTest {
    private val free = PremiumPolicy(false)
    private val premium = PremiumPolicy(true)

    private fun draft(
        name: String = "Read",
        color: Int = HabitColors.FREE[0],
        reminders: List<Reminder> = emptyList(),
        id: Long = 0,
    ) = HabitDraft(
        id = id,
        name = name,
        icon = "📚",
        colorArgb = color,
        type = HabitType.QUANTITY,
        goalValue = 20.0,
        unit = HabitUnit.PAGES,
        stepValue = 5.0,
        startDate = Fixtures.TODAY,
        reminders = reminders,
    )

    private fun existing(count: Int) = FakeHabitRepository((1..count).map { Fixtures.habit(id = it.toLong()) })

    private fun reminders(n: Int) = (1..n).map { Reminder(time = LocalTime.of(8 + it, 0)) }

    @Test
    fun `saves a valid habit`() = runTest {
        val repo = FakeHabitRepository()
        val result = SaveHabitUseCase(repo)(draft(), free)
        assertTrue(result is SaveHabitResult.Saved)
        assertEquals(1, repo.countNonArchived())
    }

    @Test
    fun `rejects invalid input without touching the repository`() = runTest {
        val repo = FakeHabitRepository()
        val result = SaveHabitUseCase(repo)(draft(name = ""), free)
        assertEquals(SaveHabitResult.Invalid(setOf(HabitFormError.NAME_EMPTY)), result)
        assertEquals(0, repo.countNonArchived())
    }

    @Test
    fun `free plan cannot create a sixth habit`() = runTest {
        val repo = existing(FreeTier.MAX_ACTIVE_HABITS)
        val result = SaveHabitUseCase(repo)(draft(), free)
        assertEquals(SaveHabitResult.RequiresPremium(PremiumFeature.UNLIMITED_HABITS), result)
        assertEquals(FreeTier.MAX_ACTIVE_HABITS, repo.countNonArchived())
    }

    @Test
    fun `archived habits do not count toward the free limit`() = runTest {
        val repo = FakeHabitRepository(
            (1..FreeTier.MAX_ACTIVE_HABITS).map { Fixtures.habit(id = it.toLong(), archived = it == 1) },
        )
        assertTrue(SaveHabitUseCase(repo)(draft(), free) is SaveHabitResult.Saved)
    }

    @Test
    fun `premium can create unlimited habits`() = runTest {
        val repo = existing(20)
        assertTrue(SaveHabitUseCase(repo)(draft(), premium) is SaveHabitResult.Saved)
        assertEquals(21, repo.countNonArchived())
    }

    @Test
    fun `free plan cannot pick a premium colour`() = runTest {
        val result = SaveHabitUseCase(FakeHabitRepository())(draft(color = HabitColors.PREMIUM[0]), free)
        assertEquals(SaveHabitResult.RequiresPremium(PremiumFeature.CUSTOM_COLORS), result)
    }

    @Test
    fun `free plan reminder limit`() = runTest {
        val repo = FakeHabitRepository()
        val useCase = SaveHabitUseCase(repo)
        assertTrue(useCase(draft(reminders = reminders(FreeTier.MAX_REMINDERS_PER_HABIT)), free) is SaveHabitResult.Saved)
        val tooMany = useCase(draft(name = "Other", reminders = reminders(FreeTier.MAX_REMINDERS_PER_HABIT + 1)), free)
        assertEquals(SaveHabitResult.RequiresPremium(PremiumFeature.UNLIMITED_REMINDERS), tooMany)
        assertTrue(useCase(draft(name = "Other", reminders = reminders(10)), premium) is SaveHabitResult.Saved)
    }

    @Test
    fun `a lapsed subscriber can edit a habit that keeps its premium colour`() = runTest {
        val repo = FakeHabitRepository(listOf(Fixtures.habit(id = 1, color = HabitColors.PREMIUM[0])))
        val result = SaveHabitUseCase(repo)(draft(id = 1, name = "Renamed", color = HabitColors.PREMIUM[0]), free)
        assertEquals(SaveHabitResult.Saved(1), result)
        assertEquals("Renamed", repo.getHabit(1)?.name)
    }

    @Test
    fun `editing never trips the habit count limit`() = runTest {
        val repo = existing(FreeTier.MAX_ACTIVE_HABITS)
        val result = SaveHabitUseCase(repo)(draft(id = 1, name = "Renamed"), free)
        assertEquals(SaveHabitResult.Saved(1), result)
    }
}
