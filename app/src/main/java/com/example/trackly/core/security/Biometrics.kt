package com.example.trackly.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint / face unlock for the app lock (class 2 "weak" biometrics, like most banking apps' quick unlock). */
object Biometrics {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, negativeText: String, onSuccess: () -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        } catch (error: RuntimeException) {
            // The PIN pad is always there as the fallback.
        }
    }
}
