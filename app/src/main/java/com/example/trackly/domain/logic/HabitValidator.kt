package com.example.trackly.domain.logic

import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.ScheduleType

enum class HabitFormError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    GOAL_INVALID,
    STEP_INVALID,
    CUSTOM_UNIT_EMPTY,
    NO_DAYS_SELECTED,
    INTERVAL_INVALID,
}

object HabitValidator {
    const val MAX_NAME_LENGTH = 40
    const val MAX_CUSTOM_UNIT_LENGTH = 16
    const val MAX_GOAL = 1_000_000.0
    const val MAX_INTERVAL_DAYS = 365

    fun validate(draft: HabitDraft): Set<HabitFormError> {
        val errors = LinkedHashSet<HabitFormError>()
        val name = draft.name.trim()
        if (name.isEmpty()) errors += HabitFormError.NAME_EMPTY
        if (name.length > MAX_NAME_LENGTH) errors += HabitFormError.NAME_TOO_LONG

        if (draft.type != HabitType.BOOLEAN) {
            if (!(draft.goalValue > 0.0 && draft.goalValue <= MAX_GOAL)) errors += HabitFormError.GOAL_INVALID
            if (!(draft.stepValue > 0.0 && draft.stepValue <= MAX_GOAL)) errors += HabitFormError.STEP_INVALID
            if (draft.unit == HabitUnit.CUSTOM) {
                val unit = draft.customUnit?.trim().orEmpty()
                if (unit.isEmpty() || unit.length > MAX_CUSTOM_UNIT_LENGTH) errors += HabitFormError.CUSTOM_UNIT_EMPTY
            }
        }

        when (draft.schedule.type) {
            ScheduleType.SPECIFIC_DAYS ->
                if (draft.schedule.days.isEmpty()) errors += HabitFormError.NO_DAYS_SELECTED
            ScheduleType.EVERY_N_DAYS ->
                if (draft.schedule.intervalDays !in 2..MAX_INTERVAL_DAYS) errors += HabitFormError.INTERVAL_INVALID
            else -> Unit
        }
        return errors
    }

    /** Boolean habits always have a goal of exactly one completion. */
    fun normalize(draft: HabitDraft): HabitDraft {
        val trimmedName = draft.name.trim()
        return if (draft.type == HabitType.BOOLEAN) {
            draft.copy(
                name = trimmedName,
                goalValue = 1.0,
                stepValue = 1.0,
                unit = HabitUnit.TIMES,
                customUnit = null,
            )
        } else {
            draft.copy(
                name = trimmedName,
                customUnit = if (draft.unit == HabitUnit.CUSTOM) draft.customUnit?.trim() else null,
            )
        }
    }
}
