package com.example.trackly.domain

import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.logic.HabitValidator
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class HabitValidatorTest {
    private fun draft(
        name: String = "Read",
        type: HabitType = HabitType.QUANTITY,
        goal: Double = 20.0,
        step: Double = 5.0,
        unit: HabitUnit = HabitUnit.PAGES,
        customUnit: String? = null,
        schedule: HabitSchedule = HabitSchedule.Daily,
    ) = HabitDraft(
        name = name,
        icon = "📚",
        colorArgb = HabitColors.DEFAULT,
        type = type,
        goalValue = goal,
        unit = unit,
        customUnit = customUnit,
        stepValue = step,
        schedule = schedule,
        startDate = Fixtures.TODAY,
    )

    @Test
    fun `a valid draft has no errors`() {
        assertTrue(HabitValidator.validate(draft()).isEmpty())
    }

    @Test
    fun `name is required and length limited`() {
        assertEquals(setOf(HabitFormError.NAME_EMPTY), HabitValidator.validate(draft(name = "   ")))
        assertEquals(
            setOf(HabitFormError.NAME_TOO_LONG),
            HabitValidator.validate(draft(name = "x".repeat(HabitValidator.MAX_NAME_LENGTH + 1))),
        )
    }

    @Test
    fun `goal and step must be positive for measurable habits`() {
        assertTrue(HabitFormError.GOAL_INVALID in HabitValidator.validate(draft(goal = 0.0)))
        assertTrue(HabitFormError.GOAL_INVALID in HabitValidator.validate(draft(goal = -1.0)))
        assertTrue(HabitFormError.STEP_INVALID in HabitValidator.validate(draft(step = 0.0)))
    }

    @Test
    fun `boolean habits ignore goal and step`() {
        val d = draft(type = HabitType.BOOLEAN, goal = 0.0, step = 0.0, unit = HabitUnit.TIMES)
        assertTrue(HabitValidator.validate(d).isEmpty())
        val normalized = HabitValidator.normalize(d)
        assertEquals(1.0, normalized.goalValue, 0.0)
        assertEquals(1.0, normalized.stepValue, 0.0)
    }

    @Test
    fun `a custom unit needs a label`() {
        assertTrue(
            HabitFormError.CUSTOM_UNIT_EMPTY in HabitValidator.validate(draft(unit = HabitUnit.CUSTOM, customUnit = " ")),
        )
        assertTrue(HabitValidator.validate(draft(unit = HabitUnit.CUSTOM, customUnit = "cups")).isEmpty())
    }

    @Test
    fun `specific days need at least one day`() {
        val none = HabitSchedule(ScheduleType.SPECIFIC_DAYS, emptySet())
        assertEquals(setOf(HabitFormError.NO_DAYS_SELECTED), HabitValidator.validate(draft(schedule = none)))
        val some = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY))
        assertTrue(HabitValidator.validate(draft(schedule = some)).isEmpty())
    }

    @Test
    fun `interval schedules must be between 2 and 365 days`() {
        fun interval(n: Int) = draft(schedule = HabitSchedule(ScheduleType.EVERY_N_DAYS, intervalDays = n))
        assertEquals(setOf(HabitFormError.INTERVAL_INVALID), HabitValidator.validate(interval(1)))
        assertEquals(setOf(HabitFormError.INTERVAL_INVALID), HabitValidator.validate(interval(366)))
        assertTrue(HabitValidator.validate(interval(2)).isEmpty())
    }

    @Test
    fun `normalize trims names and drops stale custom units`() {
        val n = HabitValidator.normalize(draft(name = "  Read  ", customUnit = "junk"))
        assertEquals("Read", n.name)
        assertNull(n.customUnit)
    }
}
