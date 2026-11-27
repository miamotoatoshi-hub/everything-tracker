package com.example.trackly.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.trackly.R

/** "7 days in a row!" — shown when today's check-in reaches a streak milestone. */
@Composable
fun StreakMilestoneDialog(
    days: Int,
    habitName: String,
    icon: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    CelebrationDialog(
        emoji = "🔥",
        title = pluralStringResource(R.plurals.milestone_title, days, days),
        subtitle = "$icon $habitName".trim(),
        body = stringResource(R.string.milestone_body),
        confirmLabel = stringResource(R.string.milestone_ok),
        onDismiss = onDismiss,
        shareLabel = stringResource(R.string.milestone_share),
        onShare = onShare,
        tag = "milestone_dialog",
    )
}

/** "Perfect day!" — every habit due today is done. */
@Composable
fun PerfectDayDialog(habitCount: Int, onShare: () -> Unit, onDismiss: () -> Unit) {
    CelebrationDialog(
        emoji = "🎉",
        title = stringResource(R.string.perfect_day_title),
        subtitle = null,
        body = pluralStringResource(R.plurals.perfect_day_body, habitCount, habitCount),
        confirmLabel = stringResource(R.string.milestone_ok),
        onDismiss = onDismiss,
        shareLabel = stringResource(R.string.milestone_share),
        onShare = onShare,
        tag = "perfect_day_dialog",
    )
}
