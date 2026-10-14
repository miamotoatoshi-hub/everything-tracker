package com.example.trackly.domain.model

/** Unit of measure for a habit goal. The enum name is what gets persisted. */
enum class HabitUnit {
    TIMES,
    MINUTES,
    HOURS,
    STEPS,
    KM,
    MILES,
    METERS,
    GLASSES,
    PAGES,
    CUSTOM,
    ;

    /** Sensible increment for the quick "+" button of a habit measured in this unit. */
    val defaultStep: Double
        get() = when (this) {
            TIMES, GLASSES, CUSTOM -> 1.0
            PAGES -> 5.0
            MINUTES -> 5.0
            HOURS -> 0.5
            KM, MILES -> 1.0
            METERS -> 500.0
            STEPS -> 1000.0
        }
}
