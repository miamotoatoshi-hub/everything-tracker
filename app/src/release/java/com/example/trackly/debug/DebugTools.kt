package com.example.trackly.debug

import androidx.compose.runtime.Composable
import com.example.trackly.di.AppContainer

/**
 * Release stub. The real tools (Premium override, demo data) exist only in the `debug` source set,
 * so a release build contains no way to fake Premium.
 */
object DebugTools {
    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun SettingsSection(container: AppContainer) = Unit
}
