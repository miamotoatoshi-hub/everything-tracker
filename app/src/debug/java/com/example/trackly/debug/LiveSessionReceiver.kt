package com.example.trackly.debug

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.example.trackly.core.locale.AppLocales

/**
 * Debug builds only: lets the browser test session (.github/scripts/live-server.py) switch the app's
 * language and type any text, Cyrillic included, which `adb shell input text` cannot do.
 * Only the shell can send it (the receiver requires android.permission.DUMP).
 *
 *   am broadcast -n com.example.trackly/.debug.LiveSessionReceiver --es locale ru
 *   am broadcast -n com.example.trackly/.debug.LiveSessionReceiver --es text64 <base64 UTF-8>
 *   (then `input keyevent 279` pastes the text into the focused field)
 */
class LiveSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("locale")?.let { AppLocales.apply(it.ifBlank { null }) }
        intent.getStringExtra("text64")?.let { encoded ->
            val text = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("text", text))
        }
    }
}
