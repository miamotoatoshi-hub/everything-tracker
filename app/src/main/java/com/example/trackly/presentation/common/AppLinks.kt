package com.example.trackly.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.trackly.BuildConfig
import com.example.trackly.R

/** Links to the app's own Google Play page, feedback e-mail and sharing. */
object AppLinks {

    fun playStoreUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"

    /** Opens the Google Play page (the Play Store app if installed, else the browser). */
    fun openStorePage(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (error: ActivityNotFoundException) {
            openUrl(context, playStoreUrl(pkg))
        }
    }

    /**
     * Writes to the developer (SUPPORT_EMAIL in gradle.properties) with the app and Android version filled in.
     * Without a configured address, opens the Google Play page, where the developer contact is listed.
     */
    fun sendFeedback(context: Context) {
        val email = BuildConfig.SUPPORT_EMAIL
        if (email.isBlank()) {
            openStorePage(context)
            return
        }
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        val subject = context.getString(R.string.feedback_subject, version)
        val body = "\n\n—\nTrackly $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            openUrl(context, "mailto:$email")
        }
    }

    /** Opens the system share sheet with a short message and the Google Play link. */
    fun share(context: Context) {
        val text = context.getString(R.string.share_message, playStoreUrl(context.packageName))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, context.getString(R.string.settings_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (error: ActivityNotFoundException) {
            // No app can share text: nothing to do.
        }
    }
}
