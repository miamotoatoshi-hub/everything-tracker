package com.example.trackly.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackly.R
import com.example.trackly.core.security.PinHasher
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.presentation.common.LocalAppContainer
import com.example.trackly.presentation.focus.FocusTimerScreen
import com.example.trackly.presentation.lock.LockScreen
import com.example.trackly.presentation.lock.PinSetupDialog
import com.example.trackly.ui.UiTestSupport.container
import com.example.trackly.ui.UiTestSupport.resetApp
import com.example.trackly.ui.UiTestSupport.seedHabit
import com.example.trackly.ui.UiTestSupport.string
import com.example.trackly.ui.theme.TracklyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The Premium screens that guard data and log time: the PIN lock, PIN setup and the focus timer. */
@RunWith(AndroidJUnit4::class)
class PremiumFeaturesTest {
    @get:Rule val compose = createComposeRule()

    private fun type(pin: String) {
        pin.forEach { digit -> compose.onNodeWithTag("pin_key_$digit").performClick() }
        compose.waitForIdle()
    }

    @Test
    fun lockScreen_wrongPinStaysLocked_rightPinUnlocks() {
        var unlocked = false
        compose.setContent {
            TracklyTheme(darkTheme = false) {
                LockScreen(pinHash = PinHasher.hash("2580"), biometric = false, onUnlocked = { unlocked = true })
            }
        }
        type("1111")
        assertFalse(unlocked)
        compose.onNodeWithText(string(R.string.lock_wrong_pin)).assertIsDisplayed()

        type("2580")
        assertTrue(unlocked)
    }

    @Test
    fun pinSetup_needsTheSamePinTwice() {
        var saved: String? = null
        compose.setContent {
            TracklyTheme(darkTheme = false) { PinSetupDialog(onDone = { saved = it }, onDismiss = {}) }
        }
        type("1234")
        type("9999")
        assertNull(saved)
        compose.onNodeWithText(string(R.string.lock_setup_mismatch)).assertIsDisplayed()

        type("1234")
        type("1234")
        val hash = saved
        assertTrue(hash != null && PinHasher.verify("1234", hash))
        assertFalse(hash!!.contains("1234"))
    }

    @Test
    fun focusTimer_startsAndFinishesEarly() {
        resetApp()
        val id = seedHabit("Meditation", type = HabitType.DURATION, goal = 20.0, unit = HabitUnit.MINUTES, step = 5.0)
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                TracklyTheme(darkTheme = false) { FocusTimerScreen(habitId = id, onBack = {}) }
            }
        }
        compose.waitForTag("focus_start")
        compose.onNodeWithTag("focus_preset_5").performClick()
        compose.onNodeWithText("05:00").assertIsDisplayed()

        compose.onNodeWithTag("focus_start").performClick()
        compose.waitForTag("focus_finish")
        compose.onNodeWithTag("focus_finish").performClick()
        compose.waitForTag("focus_result")
        compose.onNodeWithText(string(R.string.focus_nothing_logged)).assertIsDisplayed()

        // Less than a minute: today's entry is untouched.
        val entry = runBlocking { container.habitRepository.getEntry(id, LocalDate.now()) }
        assertEquals(null, entry)
    }
}
