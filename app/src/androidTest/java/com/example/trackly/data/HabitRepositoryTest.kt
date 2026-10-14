package com.example.trackly.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackly.data.local.TracklyDatabase
import com.example.trackly.data.repository.HabitRepositoryImpl
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Exercises the real Room schema through the repository, on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryTest {
    private lateinit var db: TracklyDatabase
    private lateinit var repo: HabitRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 14)
    private var changes = 0

    @Before
    fun setUp() {
        db = TracklyDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = HabitRepositoryImpl(db, clock) { changes++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(name: String = "Water", reminders: List<Reminder> = emptyList()) = HabitDraft(
        name = name,
        icon = "💧",
        colorArgb = HabitColors.FREE[5],
        type = HabitType.COUNTER,
        goalValue = 8.0,
        unit = HabitUnit.GLASSES,
        stepValue = 1.0,
        schedule = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        startDate = today.minusDays(30),
        reminders = reminders,
    )

    @Test
    fun createHabit_roundTripsEveryField() = runBlocking {
        val id = repo.createHabit(draft(reminders = listOf(Reminder(time = LocalTime.of(8, 30), message = "Drink!"))))
        val habit = repo.getHabit(id)
        assertNotNull(habit)
        habit!!
        assertEquals("Water", habit.name)
        assertEquals(HabitType.COUNTER, habit.type)
        assertEquals(HabitUnit.GLASSES, habit.unit)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), habit.schedule.days)
        assertEquals(1, habit.reminders.size)
        assertEquals(LocalTime.of(8, 30), habit.reminders.single().time)
        assertEquals("Drink!", habit.reminders.single().message)
        assertEquals(today.minusDays(30), habit.startDate)
        assertTrue(changes > 0)
    }

    @Test
    fun setEntryValue_storesCompletionAndZeroRemovesIt() = runBlocking {
        val id = repo.createHabit(draft())
        assertFalse(repo.setEntryValue(id, today, 3.0)!!.completed)
        assertTrue(repo.setEntryValue(id, today, 8.0)!!.completed)
        assertEquals(1, repo.observeEntries(id).first().size) // upsert, not duplicate
        assertNull(repo.setEntryValue(id, today, 0.0))
        assertTrue(repo.observeEntries(id).first().isEmpty())
    }

    @Test
    fun addToEntry_accumulatesAndNeverGoesNegative() = runBlocking {
        val id = repo.createHabit(draft())
        repo.addToEntry(id, today, 1.0)
        repo.addToEntry(id, today, 1.0)
        assertEquals(2.0, repo.getEntry(id, today)!!.value, 0.0)
        assertNull(repo.addToEntry(id, today, -5.0))
    }

    @Test
    fun updateHabit_reevaluatesTodaysCompletionAgainstTheNewGoal() = runBlocking {
        val id = repo.createHabit(draft())
        repo.setEntryValue(id, today, 8.0)
        assertTrue(repo.getEntry(id, today)!!.completed)
        repo.updateHabit(HabitDraft.from(repo.getHabit(id)!!).copy(goalValue = 10.0))
        assertFalse(repo.getEntry(id, today)!!.completed)
    }

    @Test
    fun deleteHabit_cascadesToEntriesAndReminders() = runBlocking {
        val id = repo.createHabit(draft(reminders = listOf(Reminder(time = LocalTime.of(9, 0)))))
        repo.setEntryValue(id, today, 2.0)
        repo.deleteHabit(id)
        assertNull(repo.getHabit(id))
        assertTrue(repo.observeEntries().first().isEmpty())
    }

    @Test
    fun archive_excludesFromTheActiveCount() = runBlocking {
        val id = repo.createHabit(draft())
        repo.createHabit(draft("Yoga"))
        assertEquals(2, repo.countNonArchived())
        repo.setArchived(id, true)
        assertEquals(1, repo.countNonArchived())
        assertTrue(repo.getHabit(id)!!.isArchived)
    }

    @Test
    fun pauseAndResume_recordsAPauseRange() = runBlocking {
        val id = repo.createHabit(draft())
        repo.setPaused(id, true, today.minusDays(3))
        assertTrue(repo.getHabit(id)!!.isPaused)
        repo.setPaused(id, false, today)
        val habit = repo.getHabit(id)!!
        assertFalse(habit.isPaused)
        assertEquals(today.minusDays(1), habit.pauses.single().to)
        assertTrue(habit.isPausedOn(today.minusDays(2)))
        assertFalse(habit.isPausedOn(today))
    }

    @Test
    fun pausingAndResumingTheSameDay_leavesNoPause() = runBlocking {
        val id = repo.createHabit(draft())
        repo.setPaused(id, true, today)
        repo.setPaused(id, false, today)
        assertTrue(repo.getHabit(id)!!.pauses.isEmpty())
    }

    @Test
    fun updateOrder_persistsTheNewOrdering() = runBlocking {
        val a = repo.createHabit(draft("A"))
        val b = repo.createHabit(draft("B"))
        val c = repo.createHabit(draft("C"))
        repo.updateOrder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), repo.observeHabits().first().map { it.name })
    }

    @Test
    fun streakIsComputedFromStoredEntries() = runBlocking {
        val daily = draft().copy(schedule = HabitSchedule.Daily)
        val id = repo.createHabit(daily)
        for (offset in 0L..2L) repo.setEntryValue(id, today.minusDays(offset), 8.0)
        val habit = repo.getHabit(id)!!
        val streak = StreakCalculator.compute(habit, repo.observeEntries(id).first(), today)
        assertEquals(3, streak.current)
        assertEquals(3, streak.best)
    }
}
