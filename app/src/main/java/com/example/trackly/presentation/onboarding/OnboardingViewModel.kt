package com.example.trackly.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.model.FreeTier
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.domain.usecase.SaveHabitUseCase
import com.example.trackly.presentation.common.HabitTemplates
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface OnboardingEvent {
    /** [openCreate] = the user asked to build a custom habit next. */
    data class Finished(val openCreate: Boolean) : OnboardingEvent
    data class LimitReached(val max: Int) : OnboardingEvent
    data object Failed : OnboardingEvent
}

class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val saveHabit: SaveHabitUseCase,
    private val policy: StateFlow<PremiumPolicy>,
    private val todayProvider: TodayProvider,
) : ViewModel() {

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    fun toggle(key: String) {
        val current = _selected.value
        if (key in current) {
            _selected.value = current - key
            return
        }
        val limit = policy.value.remainingHabitSlots(0)
        if (current.size >= limit) {
            _events.tryEmit(OnboardingEvent.LimitReached(FreeTier.MAX_ACTIVE_HABITS))
        } else {
            _selected.update { it + key }
        }
    }

    /** Creates the chosen templates and marks onboarding as done. [names] maps template key -> localized name. */
    fun finish(names: Map<String, String>, openCreate: Boolean) {
        if (_saving.value) return
        _saving.value = true
        launchSafely(onError = {
            _saving.value = false
            _events.emit(OnboardingEvent.Failed)
        }) {
            val units = settings.current().unitSystem
            val today = todayProvider.current()
            HabitTemplates.all
                .filter { it.key in _selected.value }
                .forEach { template ->
                    val name = names[template.key] ?: return@forEach
                    saveHabit(template.toDraft(name, today, units), policy.value)
                }
            settings.setOnboardingCompleted(true)
            _saving.value = false
            _events.emit(OnboardingEvent.Finished(openCreate))
        }
    }

    /** Skips template selection entirely. */
    fun skip() = finish(emptyMap(), openCreate = false)
}
