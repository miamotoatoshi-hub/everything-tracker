package com.example.trackly.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.random.Random

/**
 * A full-screen celebration: confetti over the whole screen and a card with the message.
 * Tapping outside the card dismisses it, like an ordinary dialog.
 */
@Composable
fun CelebrationDialog(
    emoji: String,
    title: String,
    subtitle: String?,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    shareLabel: String? = null,
    onShare: (() -> Unit)? = null,
    tag: String = "celebration_dialog",
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Confetti(modifier = Modifier.fillMaxSize())
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    // The card itself must not dismiss on tap.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .testTag(tag),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(emoji, fontSize = 52.sp)
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (shareLabel != null && onShare != null) {
                            TextButton(onClick = onShare, modifier = Modifier.testTag("${tag}_share")) { Text(shareLabel) }
                        }
                        Button(onClick = onDismiss, modifier = Modifier.testTag("${tag}_ok")) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

private class ConfettiPiece(random: Random) {
    val x0 = random.nextFloat()
    val delay = random.nextFloat() * 0.35f
    val drift = (random.nextFloat() - 0.5f) * 0.25f
    val fall = 0.55f + random.nextFloat() * 0.45f
    val spin = (random.nextFloat() - 0.5f) * 900f
    val angle = random.nextFloat() * 360f
    val wide = 7f + random.nextFloat() * 6f
    val tall = 4f + random.nextFloat() * 4f
    val color = random.nextInt(CONFETTI_COLORS.size)
}

private val CONFETTI_COLORS = listOf(
    Color(0xFF7C7CF8), Color(0xFFFFB020), Color(0xFF34C38F),
    Color(0xFFFF5C8A), Color(0xFF4FC3F7), Color(0xFFFFE066),
)

/**
 * Confetti falling once over the area. With animations turned off in the system settings the animation
 * finishes immediately, so nothing is drawn.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 110, durationMillis: Int = 3200) {
    val particles = remember { Random(System.nanoTime()).let { random -> List(pieces) { ConfettiPiece(random) } } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val fade = if (t > 0.8f) (1f - t) / 0.2f else 1f
        for (p in particles) {
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val x = size.width * (p.x0 + p.drift * local)
            val y = -24.dp.toPx() + (size.height + 48.dp.toPx()) * p.fall * local * (0.6f + 0.4f * local)
            val w = p.wide.dp.toPx()
            val h = p.tall.dp.toPx()
            rotate(degrees = p.angle + p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = CONFETTI_COLORS[p.color],
                    topLeft = Offset(x - w / 2f, y - h / 2f),
                    size = Size(w, h),
                    alpha = fade,
                )
            }
        }
    }
}
