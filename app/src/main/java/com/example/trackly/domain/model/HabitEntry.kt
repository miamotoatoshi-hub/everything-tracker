package com.example.trackly.domain.model

import java.time.LocalDate

/** Progress recorded for one habit on one day. A missing entry means "nothing logged". */
data class HabitEntry(
    val habitId: Long,
    val date: LocalDate,
    val value: Double,
    /** The goal that was in effect when the value was logged. */
    val goal: Double,
    val completed: Boolean,
    val updatedAt: Long = 0L,
)
