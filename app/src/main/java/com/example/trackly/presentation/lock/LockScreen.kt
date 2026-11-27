package com.example.trackly.presentation.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.example.trackly.R
import com.example.trackly.core.security.Biometrics
import com.example.trackly.core.security.PinHasher
import com.example.trackly.presentation.common.findActivity

/** Full-screen lock shown over the app until the PIN (or fingerprint / face) is entered. */
@Composable
fun LockScreen(pinHash: String, biometric: Boolean, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val canUseBiometric = biometric && activity != null && Biometrics.isAvailable(context)
    val promptTitle = stringResource(R.string.lock_biometric_title)
    val usePin = stringResource(R.string.lock_use_pin)
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun askBiometric() {
        if (canUseBiometric && activity != null) Biometrics.prompt(activity, promptTitle, usePin, onUnlocked)
    }

    LaunchedEffect(Unit) { askBiometric() }
    // Back leaves the app instead of revealing what is behind the lock.
    BackHandler { activity?.moveTaskToBack(true) }

    Surface(modifier = Modifier.fillMaxSize().testTag("lock_screen"), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (wrong) R.string.lock_wrong_pin else R.string.lock_enter_pin),
                style = MaterialTheme.typography.bodyLarge,
                color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(filled = pin.length)
            Spacer(Modifier.height(32.dp))
            PinPad(
                onDigit = { digit ->
                    if (pin.length < PinHasher.PIN_LENGTH) {
                        wrong = false
                        pin += digit
                        if (pin.length == PinHasher.PIN_LENGTH) {
                            if (PinHasher.verify(pin, pinHash)) onUnlocked() else wrong = true
                            pin = ""
                        }
                    }
                },
                onDelete = { pin = pin.dropLast(1) },
                onBiometric = if (canUseBiometric) ({ askBiometric() }) else null,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.lock_forgot_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Two steps: enter a new PIN, then repeat it. [onDone] receives the salted hash, never the PIN. */
@Composable
fun PinSetupDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("pin_setup"), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🔐", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (first == null) R.string.lock_setup_title else R.string.lock_setup_confirm),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mismatch) stringResource(R.string.lock_setup_mismatch) else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PinDots(filled = pin.length)
                Spacer(Modifier.height(32.dp))
                PinPad(
                    onDigit = { digit ->
                        if (pin.length < PinHasher.PIN_LENGTH) {
                            mismatch = false
                            pin += digit
                            if (pin.length == PinHasher.PIN_LENGTH) {
                                val chosen = first
                                when {
                                    chosen == null -> first = pin
                                    chosen == pin -> onDone(PinHasher.hash(pin))
                                    else -> {
                                        mismatch = true
                                        first = null
                                    }
                                }
                                pin = ""
                            }
                        }
                    },
                    onDelete = { pin = pin.dropLast(1) },
                    onBiometric = null,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("pin_dots")) {
        repeat(PinHasher.PIN_LENGTH) { index ->
            val on = index < filled
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)?) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { digit -> PadKey(tag = "pin_key_$digit", onClick = { onDigit(digit) }) { PadDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (onBiometric != null) {
                PadKey(tag = "pin_key_biometric", onClick = onBiometric) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = stringResource(R.string.lock_biometric_button),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(KEY_SIZE))
            }
            PadKey(tag = "pin_key_0", onClick = { onDigit('0') }) { PadDigit('0') }
            val deleteLabel = stringResource(R.string.action_delete)
            PadKey(tag = "pin_key_delete", onClick = onDelete, filled = false) {
                Text(
                    "⌫",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = deleteLabel },
                )
            }
        }
    }
}

private val KEY_SIZE = 72.dp

@Composable
private fun PadKey(tag: String, onClick: () -> Unit, filled: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (filled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
        modifier = Modifier.size(KEY_SIZE).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun PadDigit(digit: Char) {
    Text(digit.toString(), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
}
