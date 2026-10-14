package com.example.trackly.domain.model

/** How progress toward the daily goal is measured. The enum name is what gets persisted. */
enum class HabitType(
    val allowedUnits: List<HabitUnit>,
    val defaultUnit: HabitUnit,
) {
    BOOLEAN(listOf(HabitUnit.TIMES), HabitUnit.TIMES),
    COUNTER(listOf(HabitUnit.TIMES, HabitUnit.GLASSES, HabitUnit.CUSTOM), HabitUnit.TIMES),
    DURATION(listOf(HabitUnit.MINUTES, HabitUnit.HOURS), HabitUnit.MINUTES),
    DISTANCE(listOf(HabitUnit.KM, HabitUnit.MILES, HabitUnit.METERS, HabitUnit.STEPS), HabitUnit.KM),
    QUANTITY(listOf(HabitUnit.PAGES, HabitUnit.TIMES, HabitUnit.CUSTOM), HabitUnit.PAGES),
    ;

    val isBoolean: Boolean get() = this == BOOLEAN
}
