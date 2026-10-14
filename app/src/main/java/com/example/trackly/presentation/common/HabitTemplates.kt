package com.example.trackly.presentation.common

import androidx.annotation.StringRes
import com.example.trackly.R
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.TimeOfDay
import com.example.trackly.domain.model.UnitSystem
import java.time.LocalDate

/** A ready-made habit offered during onboarding. */
data class HabitTemplate(
    val key: String,
    @param:StringRes val nameRes: Int,
    val icon: String,
    val colorArgb: Int,
    val type: HabitType,
    val goal: Double,
    val unit: HabitUnit,
    val step: Double,
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
) {
    fun toDraft(name: String, startDate: LocalDate, units: UnitSystem): HabitDraft {
        // Distance templates follow the user's preferred unit system.
        val imperial = units == UnitSystem.IMPERIAL && unit == HabitUnit.KM
        return HabitDraft(
            name = name,
            icon = icon,
            colorArgb = colorArgb,
            type = type,
            goalValue = if (imperial) (goal * KM_TO_MILES_GOAL).coerceAtLeast(1.0) else goal,
            unit = if (imperial) HabitUnit.MILES else unit,
            stepValue = step,
            schedule = HabitSchedule.Daily,
            timeOfDay = timeOfDay,
            startDate = startDate,
        )
    }

    private companion object {
        const val KM_TO_MILES_GOAL = 0.62
    }
}

object HabitTemplates {
    val all: List<HabitTemplate> = listOf(
        HabitTemplate("water", R.string.template_water, "💧", HabitColors.FREE[5], HabitType.COUNTER, 8.0, HabitUnit.GLASSES, 1.0),
        HabitTemplate("yoga", R.string.template_yoga, "🧘", HabitColors.FREE[7], HabitType.DURATION, 30.0, HabitUnit.MINUTES, 5.0, TimeOfDay.MORNING),
        HabitTemplate("run", R.string.template_run, "🏃", HabitColors.FREE[0], HabitType.DISTANCE, 3.0, HabitUnit.KM, 0.5),
        HabitTemplate("read", R.string.template_read, "📚", HabitColors.FREE[2], HabitType.QUANTITY, 20.0, HabitUnit.PAGES, 5.0, TimeOfDay.EVENING),
        HabitTemplate("sleep", R.string.template_sleep, "😴", HabitColors.FREE[6], HabitType.DURATION, 8.0, HabitUnit.HOURS, 0.5),
        HabitTemplate("meditation", R.string.template_meditation, "🧠", HabitColors.FREE[4], HabitType.DURATION, 10.0, HabitUnit.MINUTES, 5.0, TimeOfDay.MORNING),
        HabitTemplate("vitamins", R.string.template_vitamins, "💊", HabitColors.FREE[1], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.MORNING),
        HabitTemplate("walk", R.string.template_walk, "🚶", HabitColors.FREE[3], HabitType.DISTANCE, 8000.0, HabitUnit.STEPS, 1000.0),
    )
}

/** Emoji offered in the icon picker of the habit form. */
object HabitIcons {
    val all: List<String> = listOf(
        "💧", "🧘", "🏃", "🚶", "🚴", "🏊", "🏋️", "⚽",
        "📚", "📝", "🎓", "💻", "🎸", "🎨", "🧹", "🌱",
        "😴", "🛏️", "☀️", "🌙", "🍳", "🥗", "🍎", "☕",
        "💊", "🦷", "❤️", "🧠", "🙏", "🚭", "📵", "💰",
        "🐕", "📞", "✉️", "🛒", "🎯", "⭐", "🔥", "✅",
    )
    const val DEFAULT = "✅"
}
