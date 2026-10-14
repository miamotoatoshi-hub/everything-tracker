package com.example.trackly.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.domain.model.FreeTier
import com.example.trackly.ui.UiTestSupport.resetApp
import com.example.trackly.ui.UiTestSupport.seedHabit
import com.example.trackly.ui.UiTestSupport.string
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationAndPremiumTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        resetApp()
        seedHabit("Water")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun bottomNavigation_switchesBetweenTheFourTabs() {
        compose.waitForTag("nav_calendar")
        compose.onNodeWithTag("nav_calendar").performClick()
        compose.waitForTag("calendar_body")

        compose.onNodeWithTag("nav_stats").performClick()
        compose.waitForTag("stats_body")

        compose.onNodeWithTag("nav_settings").performClick()
        compose.waitForTag("settings_body")

        compose.onNodeWithTag("nav_home").performClick()
        compose.waitForTag("home_list")
    }

    @Test
    fun premiumScreen_opensFromSettings_withPurchaseAndRestoreActions() {
        compose.waitForTag("nav_settings")
        compose.onNodeWithTag("nav_settings").performClick()
        compose.waitForTag("premium_status")
        compose.onNodeWithTag("premium_status").performClick()

        compose.waitForTag("premium_screen")
        compose.onNodeWithText(string(R.string.premium_title)).assertIsDisplayed()
        compose.onNodeWithTag("restore_purchases_button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("premium_disclosure").performScrollTo().assertIsDisplayed()
        // The price comes from Google Play: the purchase button exists but is only enabled with a loaded product.
        compose.onNodeWithTag("start_premium").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun freePlanLimit_opensThePremiumScreen() {
        repeat(FreeTier.MAX_ACTIVE_HABITS - 1) { seedHabit("Extra ${it + 1}") }
        scenario?.recreate()
        compose.waitForTag("add_habit")
        compose.onNodeWithTag("add_habit").performClick()
        compose.waitForTag("premium_screen")
    }

    @Test
    fun weeklyOverview_opensFromHome() {
        compose.waitForTag("open_weekly")
        compose.onNodeWithTag("open_weekly").performClick()
        compose.waitForTag("weekly_body")
    }
}
