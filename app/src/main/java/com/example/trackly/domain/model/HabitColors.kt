package com.example.trackly.domain.model

/**
 * Habit colour palettes as ARGB ints. [FREE] colours are available to everyone;
 * [PREMIUM] adds an extended palette (and any custom colour) for subscribers.
 */
object HabitColors {
    val FREE: List<Int> = listOf(
        0xFFEF5B5B.toInt(), // coral
        0xFFF59E42.toInt(), // orange
        0xFFE9B824.toInt(), // amber
        0xFF4CAF7A.toInt(), // green
        0xFF2BB6A8.toInt(), // teal
        0xFF3B9BE8.toInt(), // blue
        0xFF6C6CF0.toInt(), // indigo
        0xFFC166D6.toInt(), // violet
    )

    val PREMIUM: List<Int> = listOf(
        0xFFD93B6A.toInt(), // raspberry
        0xFFFF7A59.toInt(), // salmon
        0xFFB58B00.toInt(), // mustard
        0xFF8BC34A.toInt(), // lime
        0xFF2E8B57.toInt(), // forest
        0xFF00A6C0.toInt(), // cyan
        0xFF1E6FD9.toInt(), // royal blue
        0xFF4B3FBF.toInt(), // deep indigo
        0xFF9C4DCC.toInt(), // grape
        0xFFE05AA8.toInt(), // pink
        0xFF8D6E63.toInt(), // clay
        0xFF607D8B.toInt(), // slate
    )

    val DEFAULT: Int = FREE[6]

    fun isFree(colorArgb: Int): Boolean = colorArgb in FREE
}
