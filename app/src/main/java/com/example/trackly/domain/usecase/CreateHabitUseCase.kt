package com.example.trackly.domain.usecase

import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.logic.HabitValidator
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository

sealed interface SaveHabitResult {
    data class Saved(val habitId: Long) : SaveHabitResult
    data class Invalid(val errors: Set<HabitFormError>) : SaveHabitResult

    /** The free plan does not allow this; the UI should show the Premium upsell. */
    data class RequiresPremium(val feature: PremiumFeature) : SaveHabitResult
}

/** Validates a draft, enforces the plan limits and stores it. */
class SaveHabitUseCase(private val repository: HabitRepository) {

    suspend operator fun invoke(draft: HabitDraft, policy: PremiumPolicy): SaveHabitResult {
        val errors = HabitValidator.validate(draft)
        if (errors.isNotEmpty()) return SaveHabitResult.Invalid(errors)
        val normalized = HabitValidator.normalize(draft)

        val existing = if (normalized.id != 0L) repository.getHabit(normalized.id) else null

        // Limits only apply to what the user is *adding*, so a lapsed subscriber can still edit
        // everything else on a habit that was created with Premium options.
        val colorChanged = existing == null || existing.colorArgb != normalized.colorArgb
        if (colorChanged && !policy.isColorAllowed(normalized.colorArgb)) {
            return SaveHabitResult.RequiresPremium(PremiumFeature.CUSTOM_COLORS)
        }
        val remindersGrew = normalized.reminders.size > (existing?.reminders?.size ?: 0)
        if (remindersGrew && !policy.allowsReminderCount(normalized.reminders.size)) {
            return SaveHabitResult.RequiresPremium(PremiumFeature.UNLIMITED_REMINDERS)
        }

        if (existing == null) {
            if (!policy.canCreateHabit(repository.countNonArchived())) {
                return SaveHabitResult.RequiresPremium(PremiumFeature.UNLIMITED_HABITS)
            }
            return SaveHabitResult.Saved(repository.createHabit(normalized.copy(id = 0)))
        }
        repository.updateHabit(normalized)
        return SaveHabitResult.Saved(normalized.id)
    }
}
