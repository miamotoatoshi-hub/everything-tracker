package com.example.trackly.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackly.MainActivity
import com.example.trackly.billing.PlanKind
import com.example.trackly.billing.PremiumPlan
import com.example.trackly.billing.ProductState
import com.example.trackly.core.locale.AppLocales
import com.example.trackly.debug.DebugTools
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.presentation.premium.PlansCard
import com.example.trackly.ui.UiTestSupport.container
import com.example.trackly.ui.UiTestSupport.resetApp
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.waitForTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Not a correctness test: walks through every screen with demo data and saves screenshots to the app's
 * files dir (`files/screens/`), so the UI can be reviewed (and store screenshots produced).
 * CI runs it on its own and prints the images; it is skipped by the normal test run (see build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { container.settingsRepository.setThemeMode(ThemeMode.SYSTEM) }
        instrumentation.runOnMainSync { AppLocales.apply(null) }
        container.premiumOverride.flow.value = null
    }

    /** Saves a review copy (JPEG, 720 px wide); [store] also keeps a full-resolution PNG for the store listing. */
    private fun shot(name: String, store: Boolean = false) {
        compose.waitForIdle()
        Thread.sleep(900) // let animations and images settle
        val full = instrumentation.uiAutomation.takeScreenshot() ?: return
        val width = 720
        val scaled = Bitmap.createScaledBitmap(full, width, full.height * width / full.width, true)
        val dir = File(instrumentation.targetContext.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.jpg")).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (store) {
            FileOutputStream(File(dir, "store_$name.png")).use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Wait for the short brand intro shown after the system splash to fade out.
        compose.waitUntil(8_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "brand_intro"))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun seed(premium: Boolean) {
        resetApp()
        runBlocking { DebugTools.seedDemoData(container) }
        container.premiumOverride.flow.value = premium
    }

    private fun swipeUp(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { swipeUp() }
    }

    private fun tab(name: String, bodyTag: String) {
        compose.onNodeWithTag("nav_$name").performClick()
        compose.waitForTag(bodyTag)
    }

    private fun tourInCurrentLanguage(prefix: String, storeShots: Boolean = true) {
        seed(premium = true)
        launch()
        compose.waitForTag("home_list")
        shot("${prefix}01_home", store = storeShots)
        swipeUp("home_list")
        shot("${prefix}02_home_more")

        openFirstHabit()
        compose.waitForTag("details_body")
        shot("${prefix}03_details", store = storeShots)
        swipeUp("details_body")
        shot("${prefix}04_details_more")
        swipeUp("details_body")
        shot("${prefix}05_details_end")
        compose.onNodeWithTag("details_back").performClick()
        compose.waitForTag("home_list")

        tab("calendar", "calendar_body")
        shot("${prefix}06_calendar", store = storeShots)
        tab("stats", "stats_body")
        shot("${prefix}07_stats", store = storeShots)
        swipeUp("stats_body")
        shot("${prefix}08_stats_more")
        compose.onNodeWithTag("open_weekly_overview").performScrollTo().performClick()
        compose.waitForTag("weekly_body")
        shot("${prefix}09_weekly", store = storeShots)
        Espresso.pressBack()
        compose.waitForTag("stats_body")

        tab("settings", "settings_body")
        shot("${prefix}10_settings")
        swipeUp("settings_body")
        shot("${prefix}11_settings_more")
        swipeUp("settings_body")
        shot("${prefix}12_settings_end")
        tab("home", "home_list")

        compose.onNodeWithTag("add_habit").performClick()
        compose.waitForTag("habit_name_field")
        shot("${prefix}13_form", store = storeShots)
        compose.onNodeWithTag("form_close").performClick()
        compose.waitForTag("home_list")

        // A free user opening the paywall (no Google Play on the emulator: the price card shows its error state).
        container.premiumOverride.flow.value = false
        tab("settings", "settings_body")
        compose.onNodeWithTag("premium_status").performScrollTo().performClick()
        compose.waitForTag("premium_screen")
        shot("${prefix}14_paywall")
        compose.onNodeWithTag("premium_screen").performTouchInput { swipeUp() }
        shot("${prefix}15_paywall_more")
    }

    private fun openFirstHabit() {
        val isHabitCard = SemanticsMatcher("habit card") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("habit_card_") == true
        }
        compose.onAllNodes(isHabitCard).onFirst().performClick()
    }

    @Test
    fun tourLight() {
        runBlocking { container.settingsRepository.setThemeMode(ThemeMode.LIGHT) }
        tourInCurrentLanguage("en_light_")

        // Empty state and onboarding.
        resetApp()
        launch()
        compose.waitForTag("home_empty")
        shot("en_light_16_empty")
        runBlocking { container.settingsRepository.setOnboardingCompleted(false) }
        launch()
        compose.waitForTag("onboarding")
        shot("en_light_17_onboarding")
        for (page in 2..4) {
            val next = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "onboarding_next"))
            if (next.fetchSemanticsNodes().isEmpty()) break
            next.onFirst().performClick()
            shot("en_light_18_onboarding_$page")
        }
        runBlocking { container.settingsRepository.setOnboardingCompleted(true) }
    }

    @Test
    fun tourDark() {
        runBlocking { container.settingsRepository.setThemeMode(ThemeMode.DARK) }
        tourInCurrentLanguage("en_dark_", storeShots = false)
    }

    @Test
    fun tourRussian() {
        runBlocking { container.settingsRepository.setThemeMode(ThemeMode.LIGHT) }
        // Switch the language the way a user does: Settings > Language > Русский.
        resetApp()
        launch()
        tab("settings", "settings_body")
        compose.onNodeWithTag("setting_language").performScrollTo().performClick()
        compose.onNodeWithText("Русский").performClick()
        compose.waitForIdle()
        Thread.sleep(2_500) // the activity is recreated in the new language
        shot("ru_light_00_language_switched")
        tourInCurrentLanguage("ru_light_")
    }
}

/** The plan picker with realistic Google Play data, rendered on its own (no Play Store on the emulator). */
@RunWith(AndroidJUnit4::class)
class PaywallPlansShot {
    @get:Rule val compose = createComposeRule()

    private fun plan(kind: PlanKind, price: String, micros: Long, period: String?, trial: Int? = null) = PremiumPlan(
        id = kind.name.lowercase(),
        productId = "trackly_premium",
        kind = kind,
        formattedPrice = price,
        priceAmountMicros = micros,
        currencyCode = "USD",
        billingPeriod = period,
        freeTrialDays = trial,
    )

    private fun render(dark: Boolean, name: String) {
        val state = ProductState.Available(
            listOf(
                plan(PlanKind.YEARLY, "$19.99", 19_990_000, "P1Y", trial = 7),
                plan(PlanKind.MONTHLY, "$2.99", 2_990_000, "P1M"),
                plan(PlanKind.LIFETIME, "$34.99", 34_990_000, null),
            ),
        )
        compose.setContent {
            TracklyTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        PlansCard(state = state, canPurchase = true, onPurchase = {}, onRetry = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val full = instrumentation.uiAutomation.takeScreenshot() ?: return
        val scaled = Bitmap.createScaledBitmap(full, 720, full.height * 720 / full.width, true)
        val dir = File(instrumentation.targetContext.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.jpg")).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        FileOutputStream(File(dir, "store_$name.png")).use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun plansLight() = render(dark = false, name = "plans_light")

    @Test fun plansDark() = render(dark = true, name = "plans_dark")
}
