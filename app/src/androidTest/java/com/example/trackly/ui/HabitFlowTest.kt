package com.example.trackly.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.ui.UiTestSupport.resetApp
import com.example.trackly.ui.UiTestSupport.seedHabit
import com.example.trackly.ui.UiTestSupport.string
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end UI tests for the core habit flows. Run with `./gradlew connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class HabitFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        resetApp()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun emptyState_isShownWhenThereAreNoHabits() {
        launch()
        compose.waitForText(string(R.string.home_empty_title))
        compose.onNodeWithText(string(R.string.home_create_habit)).assertIsDisplayed()
    }

    @Test
    fun createHabit_appearsOnHome() {
        launch()
        compose.waitForText(string(R.string.home_create_habit))
        compose.onNodeWithText(string(R.string.home_create_habit)).performClick()

        compose.waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextInput("Evening walk")
        compose.onNodeWithTag("save_habit").performClick()

        compose.waitForText("Evening walk")
        compose.onNodeWithText("Evening walk").assertIsDisplayed()
    }

    @Test
    fun createHabit_withoutName_showsValidationError() {
        launch()
        compose.waitForText(string(R.string.home_create_habit))
        compose.onNodeWithText(string(R.string.home_create_habit)).performClick()
        compose.waitForTag("save_habit")
        compose.onNodeWithTag("save_habit").performClick()
        compose.waitForText(string(R.string.error_name_required))
    }

    @Test
    fun completeBooleanHabit_updatesTodayProgress() {
        val id = seedHabit("Vitamins", type = HabitType.BOOLEAN, goal = 1.0, unit = HabitUnit.TIMES)
        launch()
        compose.waitForTag("quick_add_$id")
        compose.onNodeWithTag("quick_add_$id").performClick()
        compose.waitForText("100%")
    }

    @Test
    fun counterHabit_plusButtonIncrementsProgress() {
        val id = seedHabit("Water", goal = 8.0, unit = HabitUnit.GLASSES)
        launch()
        compose.waitForTag("quick_add_$id")
        compose.onNodeWithTag("quick_add_$id").performClick()
        compose.waitForText("1 / 8 ${string(R.string.unit_glasses)}")
        compose.onNodeWithTag("quick_add_$id").performClick()
        compose.waitForText("2 / 8 ${string(R.string.unit_glasses)}")
    }

    @Test
    fun editHabit_renamesIt() {
        val id = seedHabit("Water")
        launch()
        compose.waitForTag("habit_card_$id")
        compose.onNodeWithTag("habit_card_$id").performClick()
        compose.waitForTag("details_edit")
        compose.onNodeWithTag("details_edit").performClick()

        compose.waitForTag("habit_name_field")
        compose.onNodeWithTag("habit_name_field").performTextClearance()
        compose.onNodeWithTag("habit_name_field").performTextInput("Hydration")
        compose.onNodeWithTag("save_habit").performClick()

        // Back on the details screen, the title shows the new name.
        compose.waitForText("Hydration")
    }

    @Test
    fun archiveHabit_movesItToTheArchivedFilter() {
        val id = seedHabit("Water")
        launch()
        compose.waitForTag("habit_card_$id")
        compose.onNodeWithTag("habit_card_$id").performClick()
        compose.waitForTag("details_menu")
        compose.onNodeWithTag("details_menu").performClick()
        compose.onNodeWithTag("menu_archive").performClick()

        // Archiving returns to Home, which now has no active habit but one archived habit.
        compose.waitForTag("filter_archived")
        compose.onNodeWithTag("filter_archived").performClick()
        compose.waitForText("Water")
    }

    @Test
    fun deleteHabit_asksForConfirmation_andRemovesIt() {
        val id = seedHabit("Water")
        launch()
        compose.waitForTag("habit_card_$id")
        compose.onNodeWithTag("habit_card_$id").performClick()
        compose.waitForTag("details_menu")
        compose.onNodeWithTag("details_menu").performClick()
        compose.onNodeWithTag("menu_delete").performClick()

        compose.waitForText(string(R.string.delete_habit_title))
        compose.onNodeWithText(string(R.string.action_delete)).performClick()

        compose.waitForText(string(R.string.home_empty_title))
    }
}
