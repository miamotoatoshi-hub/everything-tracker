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
        HabitTemplate("workout", R.string.template_workout, "🏋️", HabitColors.FREE[0], HabitType.DURATION, 30.0, HabitUnit.MINUTES, 5.0),
        HabitTemplate("stretch", R.string.template_stretch, "🤸", HabitColors.FREE[7], HabitType.DURATION, 10.0, HabitUnit.MINUTES, 5.0, TimeOfDay.MORNING),
        HabitTemplate("early_rise", R.string.template_early_rise, "☀️", HabitColors.FREE[2], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.MORNING),
        HabitTemplate("journal", R.string.template_journal, "📝", HabitColors.FREE[6], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.EVENING),
        HabitTemplate("gratitude", R.string.template_gratitude, "🙏", HabitColors.FREE[4], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.EVENING),
        HabitTemplate("language", R.string.template_language, "🗣️", HabitColors.FREE[5], HabitType.DURATION, 15.0, HabitUnit.MINUTES, 5.0),
        HabitTemplate("study", R.string.template_study, "🎓", HabitColors.FREE[1], HabitType.DURATION, 45.0, HabitUnit.MINUTES, 15.0),
        HabitTemplate("music", R.string.template_music, "🎸", HabitColors.FREE[0], HabitType.DURATION, 20.0, HabitUnit.MINUTES, 5.0),
        HabitTemplate("fruit", R.string.template_fruit, "🍎", HabitColors.FREE[3], HabitType.COUNTER, 5.0, HabitUnit.TIMES, 1.0),
        HabitTemplate("no_sugar", R.string.template_no_sugar, "🍬", HabitColors.FREE[1], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
        HabitTemplate("cook", R.string.template_cook, "🍳", HabitColors.FREE[2], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.EVENING),
        HabitTemplate("cycling", R.string.template_cycling, "🚴", HabitColors.FREE[3], HabitType.DISTANCE, 10.0, HabitUnit.KM, 1.0),
        HabitTemplate("swim", R.string.template_swim, "🏊", HabitColors.FREE[5], HabitType.DURATION, 30.0, HabitUnit.MINUTES, 5.0),
        HabitTemplate("floss", R.string.template_floss, "🦷", HabitColors.FREE[4], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.EVENING),
        HabitTemplate("no_smoking", R.string.template_no_smoking, "🚭", HabitColors.FREE[6], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
        HabitTemplate("screen_free", R.string.template_screen_free, "📵", HabitColors.FREE[7], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0, TimeOfDay.EVENING),
        HabitTemplate("tidy", R.string.template_tidy, "🧹", HabitColors.FREE[2], HabitType.DURATION, 10.0, HabitUnit.MINUTES, 5.0),
        HabitTemplate("save_money", R.string.template_save_money, "💰", HabitColors.FREE[3], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
        HabitTemplate("call_family", R.string.template_call_family, "📞", HabitColors.FREE[0], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
        HabitTemplate("dog_walk", R.string.template_dog_walk, "🐕", HabitColors.FREE[1], HabitType.BOOLEAN, 1.0, HabitUnit.TIMES, 1.0),
    )

    /** The short list offered during onboarding; the habit form offers [all] as ideas. */
    val onboarding: List<HabitTemplate> = all.take(8)
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
