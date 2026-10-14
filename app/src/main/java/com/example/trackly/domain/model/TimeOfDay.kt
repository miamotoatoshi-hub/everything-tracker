package com.example.trackly.domain.model

import java.time.LocalTime

/** When during the day the user plans to do a habit. Used for labelling and default reminder times. */
enum class TimeOfDay(val suggestedReminder: LocalTime?) {
    ANYTIME(null),
    MORNING(LocalTime.of(8, 0)),
    AFTERNOON(LocalTime.of(12, 0)),
    EVENING(LocalTime.of(18, 0)),
    CUSTOM(null),
}
