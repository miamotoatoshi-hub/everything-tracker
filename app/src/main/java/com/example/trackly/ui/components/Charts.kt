package com.example.trackly.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One bar of a [BarChart]. [fraction] is 0..1 of the chart height. */
data class BarData(
    val label: String,
    val fraction: Float,
    val description: String,
    val highlighted: Boolean = false,
    val muted: Boolean = false,
)

/**
 * Minimal, uncluttered bar chart built from composables (so text scales and TalkBack can read
 * every bar). Bars animate to their height.
 */
@Composable
fun BarChart(
    bars: List<BarData>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 132.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    maxBarWidth: Dp = 28.dp,
    showLabels: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (bars.size > 14) 2.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            val animated by animateFloatAsState(
                targetValue = bar.fraction.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 500),
                label = "bar",
            )
            Column(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = bar.description
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.height(chartHeight).widthIn(max = maxBarWidth).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .background(trackColor.copy(alpha = if (bar.muted) 0.35f else 0.7f), RoundedCornerShape(8.dp)),
                    )
                    if (animated > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(animated.coerceAtLeast(0.04f))
                                .fillMaxWidth()
                                .background(
                                    if (bar.highlighted) barColor else barColor.copy(alpha = 0.72f),
                                    RoundedCornerShape(8.dp),
                                ),
                        )
                    }
                }
                if (showLabels) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (bar.highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/** A thin horizontal bar used for per-habit rates and goal progress. */
@Composable
fun LinearRate(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "rate",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).background(trackColor, RoundedCornerShape(height)),
    ) {
        if (animated > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceAtLeast(0.03f))
                    .background(color, RoundedCornerShape(height)),
            )
        }
    }
}
