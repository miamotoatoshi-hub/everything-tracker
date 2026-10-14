@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackly.R
import com.example.trackly.domain.model.HabitType
import com.example.trackly.presentation.common.formatValue
import com.example.trackly.presentation.common.progressText
import com.example.trackly.presentation.common.rememberLocale
import com.example.trackly.presentation.common.unitLabel
import com.example.trackly.ui.components.LinearRate
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import com.example.trackly.ui.theme.habitContainer

/** Readable foreground for content drawn on top of a habit's accent colour. */
fun Color.onAccent(): Color = if (luminance() > 0.42f) Color(0xFF1B1B1F) else Color.White

@Composable
fun HabitCard(
    item: HabitItem,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onLongPressAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habit = item.habit
    val isDark = TracklyTheme.extras.isDark
    val base = Color(habit.colorArgb)
    val accent = base.habitAccent(isDark)
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val container by animateColorAsState(
        targetValue = base.habitContainer(surface, isDark, if (item.completed) 1f else 0f),
        animationSpec = tween(durationMillis = 350),
        label = "cardBackground",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("habit_card_${habit.id}"),
        shape = RoundedCornerShape(24.dp),
        color = container,
        tonalElevation = 0.dp,
        shadowElevation = if (isDark) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = habit.icon, fontSize = 26.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = progressText(habit, item.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (habit.type != HabitType.BOOLEAN) {
                    Spacer(Modifier.size(4.dp))
                    LinearRate(
                        fraction = item.progress,
                        color = accent,
                        height = 6.dp,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }
                if (habit.isPaused) {
                    Spacer(Modifier.size(4.dp))
                    PausedChip()
                } else if (item.streak > 0) {
                    Spacer(Modifier.size(4.dp))
                    StreakChip(streak = item.streak)
                }
            }

            QuickActionButton(item = item, accent = accent, onClick = onQuickAdd, onLongClick = onLongPressAdd)
        }
    }
}

@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = TracklyTheme.extras.streak,
            modifier = Modifier.size(16.dp),
        )
        AnimatedContent(
            targetState = streak,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "streak",
        ) { count ->
            Text(
                text = stringResource(R.string.streak_days, count),
                style = MaterialTheme.typography.labelMedium,
                color = TracklyTheme.extras.streak,
            )
        }
    }
}

@Composable
private fun PausedChip() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(
            imageVector = Icons.Rounded.Pause,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.habit_paused),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActionButton(
    item: HabitItem,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val habit = item.habit
    val isBoolean = habit.type == HabitType.BOOLEAN
    val done = item.completed
    val locale = rememberLocale()
    val unit = unitLabel(habit)

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (done) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "actionScale",
    )
    val background by animateColorAsState(
        targetValue = if (done) accent else accent.copy(alpha = 0.18f),
        animationSpec = tween(durationMillis = 300),
        label = "actionBackground",
    )
    val description = when {
        isBoolean && done -> stringResource(R.string.habit_card_mark_not_done, habit.name)
        isBoolean -> stringResource(R.string.habit_card_mark_done, habit.name)
        else -> stringResource(R.string.habit_card_add, formatValue(habit.stepValue, locale), unit, habit.name)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                onClickLabel = description,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClickLabel = if (isBoolean) null else stringResource(R.string.habit_card_set_value),
                onLongClick = if (isBoolean) {
                    null
                } else {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            )
            .semantics { contentDescription = description; role = Role.Button }
            .testTag("quick_add_${habit.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (done) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = null,
            tint = if (done) accent.onAccent() else accent,
            modifier = Modifier.size(26.dp),
        )
    }
}
