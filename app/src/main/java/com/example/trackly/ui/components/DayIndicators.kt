package com.example.trackly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackly.domain.logic.DayState

/**
 * Visual state of one day:
 *  completed = filled, partial = half-filled, missed = empty ring, pending = bold ring,
 *  future / not scheduled = muted dot.
 */
@Composable
fun DayDot(
    state: DayState,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    isToday: Boolean = false,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val todayRing = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val diameter = this.size.minDimension
        val inset = stroke / 2f
        val ringSize = Size(diameter - stroke, diameter - stroke)
        val topLeft = Offset(inset, inset)
        when (state) {
            DayState.COMPLETED -> {
                drawCircle(color = color, radius = diameter / 2f)
            }
            DayState.PARTIAL -> {
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = ringSize,
                )
                drawCircle(color = color, radius = (diameter - stroke) / 2f, style = Stroke(width = stroke))
            }
            DayState.MISSED -> {
                drawCircle(color = track, radius = (diameter - stroke) / 2f, style = Stroke(width = stroke))
            }
            DayState.PENDING -> {
                drawCircle(color = color, radius = (diameter - stroke) / 2f, style = Stroke(width = stroke * 1.25f))
            }
            DayState.FUTURE, DayState.NOT_SCHEDULED -> {
                drawCircle(color = muted, radius = diameter * 0.09f)
            }
        }
        if (isToday) {
            drawCircle(
                color = todayRing,
                radius = diameter / 2f - 0.5.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}
