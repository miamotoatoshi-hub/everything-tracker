package com.example.trackly.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("milestone_dialog"),
        icon = { Text("🔥", fontSize = 48.sp) },
        title = {
            Text(
                pluralStringResource(R.plurals.milestone_title, days, days),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "$icon $habitName".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.milestone_body), textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("milestone_ok")) {
                Text(stringResource(R.string.milestone_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onShare, modifier = Modifier.testTag("milestone_share")) {
                Text(stringResource(R.string.milestone_share))
            }
        },
    )
}
