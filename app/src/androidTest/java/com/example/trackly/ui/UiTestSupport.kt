package com.example.trackly.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackly.TracklyApp
import com.example.trackly.di.AppContainer
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PremiumPolicy
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

object UiTestSupport {
    val container: AppContainer
        get() = ApplicationProvider.getApplicationContext<TracklyApp>().container

    fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    /** Empty database, onboarding finished: tests start from a clean "installed and set up" app. */
    fun resetApp() = runBlocking {
        val repo = container.habitRepository
        repo.getHabits().forEach { repo.deleteHabit(it.id) }
        container.settingsRepository.setOnboardingCompleted(true)
    }

    fun seedHabit(
        name: String,
        type: HabitType = HabitType.COUNTER,
        goal: Double = 8.0,
        unit: HabitUnit = HabitUnit.GLASSES,
        step: Double = 1.0,
    ): Long = runBlocking {
        container.habitRepository.createHabit(
            HabitDraft(
                name = name,
                icon = "💧",
                colorArgb = HabitColors.FREE[5],
                type = type,
                goalValue = goal,
                unit = unit,
                stepValue = step,
                schedule = HabitSchedule.Daily,
                startDate = LocalDate.now().minusDays(7),
            ),
        )
    }

    @Suppress("unused")
    val freePolicy = PremiumPolicy(isPremium = false)
}

fun ComposeTestRule.waitForTag(tag: String, timeoutMillis: Long = 8_000) {
    waitUntil(timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 8_000) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
