package com.example.trackly.core.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the PIN screen must be shown. The app starts locked; the root unlocks right away when no
 * PIN is set. After [GRACE_MILLIS] in the background it locks again (a share sheet or the purchase
 * dialog does not count as leaving).
 */
class AppLock(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun onBackground() {
        backgroundedAt = now()
    }

    fun onForeground(lockEnabled: Boolean) {
        val since = backgroundedAt
        backgroundedAt = null
        if (lockEnabled && since != null && now() - since >= GRACE_MILLIS) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}
