package com.example.trackly.presentation.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.domain.model.TimeOfDay
import com.example.trackly.domain.model.UnitSystem
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.domain.usecase.SaveHabitResult
import com.example.trackly.domain.usecase.SaveHabitUseCase
import com.example.trackly.presentation.common.HabitIcons
import com.example.trackly.presentation.common.formatValue
import com.example.trackly.presentation.common.launchSafely
import com.example.trackly.presentation.common.parseNumber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class ReminderDraft(
    /** Stable id for list keys and editing (not the database id). */
    val key: Long,
    val id: Long = 0,
    val time: LocalTime,
    val message: String = "",
    val enabled: Boolean = true,
    val everyDay: Boolean = false,
)

data class HabitFormState(
    val isEdit: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val icon: String = HabitIcons.DEFAULT,
    val colorArgb: Int = HabitColors.DEFAULT,
    val type: HabitType = HabitType.BOOLEAN,
    val goalText: String = "1",
    val stepText: String = "1",
    val unit: HabitUnit = HabitUnit.TIMES,
    val customUnit: String = "",
    val scheduleType: ScheduleType = ScheduleType.EVERY_DAY,
    val days: Set<DayOfWeek> = emptySet(),
    val intervalText: String = "2",
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val startDate: LocalDate = LocalDate.now(),
    val reminders: List<ReminderDraft> = emptyList(),
    val errors: Set<HabitFormError> = emptySet(),
    val saving: Boolean = false,
)

sealed interface HabitFormEvent {
    data class Saved(val habitId: Long) : HabitFormEvent
    data class RequiresPremium(val feature: PremiumFeature) : HabitFormEvent
    data object Failed : HabitFormEvent
}

/** Drives both the "new habit" and "edit habit" screens. [habitId] == 0 means create. */
class HabitFormViewModel(
    private val habitId: Long,
    private val habits: HabitRepository,
    private val saveHabit: SaveHabitUseCase,
    private val settings: SettingsRepository,
    private val policy: StateFlow<PremiumPolicy>,
    private val todayProvider: TodayProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(HabitFormState(isEdit = habitId != 0L, startDate = todayProvider.current()))
    val state: StateFlow<HabitFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HabitFormEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HabitFormEvent> = _events.asSharedFlow()

    val firstDayOfWeek: StateFlow<DayOfWeek> = settings.settings
        .map { it.firstDayOfWeek.resolve(Locale.getDefault()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayOfWeek.MONDAY)

    private var initial: HabitFormState = _state.value
    private var nextReminderKey = 1L
    private var unitSystem = UnitSystem.METRIC

    val isDirty: Boolean get() = _state.value.copy(saving = false, errors = emptySet()) != initial

    init {
        launchSafely(onError = { _events.emit(HabitFormEvent.Failed) }) {
            unitSystem = settings.current().unitSystem
            if (habitId != 0L) {
                val habit = habits.getHabit(habitId)
                if (habit == null) {
                    _state.update { it.copy(loaded = true, notFound = true) }
                } else {
                    val loadedState = HabitFormState(
                        isEdit = true,
                        loaded = true,
                        name = habit.name,
                        icon = habit.icon,
                        colorArgb = habit.colorArgb,
                        type = habit.type,
                        goalText = formatValue(habit.goalValue, Locale.US),
                        stepText = formatValue(habit.stepValue, Locale.US),
                        unit = habit.unit,
                        customUnit = habit.customUnit.orEmpty(),
                        scheduleType = habit.schedule.type,
                        days = habit.schedule.days,
                        intervalText = habit.schedule.intervalDays.coerceAtLeast(2).toString(),
                        timeOfDay = habit.timeOfDay,
                        startDate = habit.startDate,
                        reminders = habit.reminders.map {
                            ReminderDraft(
                                key = nextReminderKey++,
                                id = it.id,
                                time = it.time,
                                message = it.message.orEmpty(),
                                enabled = it.enabled,
                                everyDay = it.repeat == ReminderRepeat.EVERY_DAY,
                            )
                        },
                    )
                    _state.value = loadedState
                    initial = loadedState
                }
            } else {
                _state.update { it.copy(loaded = true, unit = it.type.defaultUnit) }
                initial = _state.value
            }
        }
    }

    // region field changes

    fun onNameChange(value: String) = update { it.copy(name = value.take(HABIT_NAME_LIMIT), errors = it.errors - HabitFormError.NAME_EMPTY - HabitFormError.NAME_TOO_LONG) }

    fun onIconChange(value: String) = update { it.copy(icon = value) }

    fun onColorChange(argb: Int) {
        if (!policy.value.isColorAllowed(argb)) {
            _events.tryEmit(HabitFormEvent.RequiresPremium(PremiumFeature.CUSTOM_COLORS))
            return
        }
        update { it.copy(colorArgb = argb) }
    }

    fun onTypeChange(type: HabitType) = update { current ->
        if (type == current.type) return@update current
        val unit = defaultUnitFor(type)
        current.copy(
            type = type,
            unit = unit,
            customUnit = "",
            goalText = formatValue(defaultGoalFor(type, unit), Locale.US),
            stepText = formatValue(unit.defaultStep, Locale.US),
            errors = emptySet(),
        )
    }

    fun onUnitChange(unit: HabitUnit) = update {
        it.copy(
            unit = unit,
            stepText = formatValue(unit.defaultStep, Locale.US),
            errors = it.errors - HabitFormError.CUSTOM_UNIT_EMPTY,
        )
    }

    fun onGoalChange(value: String) = update { it.copy(goalText = value.take(10), errors = it.errors - HabitFormError.GOAL_INVALID) }

    fun onStepChange(value: String) = update { it.copy(stepText = value.take(10), errors = it.errors - HabitFormError.STEP_INVALID) }

    fun onCustomUnitChange(value: String) = update { it.copy(customUnit = value.take(16), errors = it.errors - HabitFormError.CUSTOM_UNIT_EMPTY) }

    fun onScheduleTypeChange(type: ScheduleType) = update {
        val days = if (type == ScheduleType.SPECIFIC_DAYS && it.days.isEmpty()) {
            setOf(todayProvider.current().dayOfWeek)
        } else {
            it.days
        }
        it.copy(scheduleType = type, days = days, errors = emptySet())
    }

    fun onToggleDay(day: DayOfWeek) = update {
        val days = if (day in it.days) it.days - day else it.days + day
        it.copy(days = days, errors = it.errors - HabitFormError.NO_DAYS_SELECTED)
    }

    fun onIntervalChange(value: String) = update { it.copy(intervalText = value.filter(Char::isDigit).take(3), errors = it.errors - HabitFormError.INTERVAL_INVALID) }

    fun onTimeOfDayChange(value: TimeOfDay) = update { it.copy(timeOfDay = value) }

    fun onStartDateChange(date: LocalDate) = update { it.copy(startDate = date) }

    // endregion

    // region reminders

    /** Returns false (and asks the UI to show the upsell) when the free plan limit is reached. */
    fun canAddReminder(): Boolean {
        val allowed = policy.value.allowsReminderCount(_state.value.reminders.size + 1)
        if (!allowed) _events.tryEmit(HabitFormEvent.RequiresPremium(PremiumFeature.UNLIMITED_REMINDERS))
        return allowed
    }

    fun suggestedReminderTime(): LocalTime =
        _state.value.timeOfDay.suggestedReminder ?: LocalTime.of(9, 0)

    fun addReminder(time: LocalTime, message: String, everyDay: Boolean) {
        if (!canAddReminder()) return
        update {
            it.copy(reminders = (it.reminders + ReminderDraft(nextReminderKey++, 0, time, message.trim(), true, everyDay)).sortedBy { r -> r.time })
        }
    }

    fun updateReminder(key: Long, time: LocalTime, message: String, everyDay: Boolean) = update {
        it.copy(
            reminders = it.reminders
                .map { r -> if (r.key == key) r.copy(time = time, message = message.trim(), everyDay = everyDay) else r }
                .sortedBy { r -> r.time },
        )
    }

    fun setReminderEnabled(key: Long, enabled: Boolean) = update {
        it.copy(reminders = it.reminders.map { r -> if (r.key == key) r.copy(enabled = enabled) else r })
    }

    fun removeReminder(key: Long) = update { it.copy(reminders = it.reminders.filterNot { r -> r.key == key }) }

    // endregion

    fun save() {
        val current = _state.value
        if (current.saving || !current.loaded) return
        _state.update { it.copy(saving = true) }
        launchSafely(onError = {
            _state.update { s -> s.copy(saving = false) }
            _events.emit(HabitFormEvent.Failed)
        }) {
            val result = saveHabit(toDraft(current), policy.value)
            when (result) {
                is SaveHabitResult.Saved -> {
                    initial = _state.value
                    _state.update { it.copy(saving = false) }
                    _events.emit(HabitFormEvent.Saved(result.habitId))
                }
                is SaveHabitResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
                is SaveHabitResult.RequiresPremium -> {
                    _state.update { it.copy(saving = false) }
                    _events.emit(HabitFormEvent.RequiresPremium(result.feature))
                }
            }
        }
    }

    private fun toDraft(s: HabitFormState): HabitDraft {
        val goal = parseNumber(s.goalText) ?: 0.0
        val step = parseNumber(s.stepText) ?: 0.0
        val interval = s.intervalText.toIntOrNull() ?: 0
        return HabitDraft(
            id = habitId,
            name = s.name,
            icon = s.icon.ifBlank { HabitIcons.DEFAULT },
            colorArgb = s.colorArgb,
            type = s.type,
            goalValue = goal,
            unit = s.unit,
            customUnit = s.customUnit.takeIf { s.unit == HabitUnit.CUSTOM },
            stepValue = step,
            schedule = HabitSchedule(type = s.scheduleType, days = s.days, intervalDays = interval),
            timeOfDay = s.timeOfDay,
            startDate = s.startDate,
            reminders = s.reminders.map {
                Reminder(
                    id = it.id,
                    habitId = habitId,
                    time = it.time,
                    message = it.message.ifBlank { null },
                    enabled = it.enabled,
                    repeat = if (it.everyDay) ReminderRepeat.EVERY_DAY else ReminderRepeat.HABIT_SCHEDULE,
                )
            },
        )
    }

    private fun defaultUnitFor(type: HabitType): HabitUnit =
        if (type == HabitType.DISTANCE && unitSystem == UnitSystem.IMPERIAL) HabitUnit.MILES else type.defaultUnit

    private fun defaultGoalFor(type: HabitType, unit: HabitUnit): Double = when (type) {
        HabitType.BOOLEAN -> 1.0
        HabitType.COUNTER -> 8.0
        HabitType.DURATION -> 30.0
        HabitType.DISTANCE -> if (unit == HabitUnit.STEPS) 8000.0 else 3.0
        HabitType.QUANTITY -> 20.0
    }

    private inline fun update(block: (HabitFormState) -> HabitFormState) {
        _state.update(block)
    }

    companion object {
        const val HABIT_NAME_LIMIT = 40
    }
}
