package com.example.trackly.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Decides where the app starts and runs the one-off branded intro. */
class MainViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _startDestination = MutableStateFlow<String?>(null)

    /** null while the first settings read is in flight (the system splash screen stays up). */
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    private val _brandIntroDone = MutableStateFlow(false)
    val brandIntroDone: StateFlow<Boolean> = _brandIntroDone.asStateFlow()

    init {
        viewModelScope.launch {
            val onboarded = try {
                settingsRepository.current().onboardingCompleted
            } catch (error: Exception) {
                false
            }
            _startDestination.value = if (onboarded) Routes.HOME else Routes.ONBOARDING
            delay(BRAND_INTRO_MILLIS)
            _brandIntroDone.value = true
        }
    }

    private companion object {
        const val BRAND_INTRO_MILLIS = 600L
    }
}
