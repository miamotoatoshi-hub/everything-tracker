package com.example.trackly

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trackly.notifications.ReminderNotifier
import com.example.trackly.presentation.app.MainViewModel
import com.example.trackly.presentation.app.TracklyRoot
import com.example.trackly.presentation.common.LocalAppContainer
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    private val container get() = (application as TracklyApp).container

    /** Habit to open because the user tapped a reminder notification. */
    private val deepLinkHabitId = MutableStateFlow<Long?>(null)

    private val mainViewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container.settingsRepository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { mainViewModel.startDestination.value == null }

        handleIntent(intent)
        container.billingRepository.connect()

        setContent {
            val pendingHabit by deepLinkHabitId.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                TracklyRoot(
                    mainViewModel = mainViewModel,
                    deepLinkHabitId = pendingHabit,
                    onDeepLinkConsumed = { deepLinkHabitId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Midnight may have passed while the app was in the background; also re-check Premium.
        container.todayProvider.refresh()
        container.billingRepository.refreshPurchases()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ReminderNotifier.ACTION_OPEN_HABIT) {
            val id = intent.getLongExtra(ReminderNotifier.EXTRA_HABIT_ID, -1L)
            if (id > 0L) deepLinkHabitId.value = id
        }
    }
}
