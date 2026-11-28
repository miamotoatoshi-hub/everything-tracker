@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.trackly.presentation.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.FocusTimer
import com.example.trackly.presentation.common.appViewModel

@Composable
fun FocusTimerScreen(habitId: Long, onBack: () -> Unit) {
    val viewModel = appViewModel(key = "focus_$habitId") {
        FocusTimerViewModel(habitId, it.habitRepository, it.todayProvider, onSessionDone = it.focusDoneNotifier::notifyIfInBackground)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // The screen stays on while the timer runs, so the countdown stays in sight.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag("focus_body"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val habit = state.habit
                if (habit != null) {
                    Text(
                        "${habit.icon} ${habit.name}".trim(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                TimerDial(progress = state.progress, remaining = FocusTimer.format(state.remainingSeconds))

                when {
                    state.finished -> Finished(minutes = state.loggedMinutes ?: 0, onDone = onBack)
                    !state.started -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FocusTimer.presetMinutes.forEach { minutes ->
                                FilterChip(
                                    selected = state.totalSeconds == minutes * 60,
                                    onClick = { viewModel.setMinutes(minutes) },
                                    label = { Text(stringResource(R.string.focus_minutes, minutes)) },
                                    modifier = Modifier.testTag("focus_preset_$minutes"),
                                )
                            }
                        }
                        Button(
                            onClick = viewModel::start,
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("focus_start"),
                            shape = RoundedCornerShape(18.dp),
                        ) { Text(stringResource(R.string.focus_start), style = MaterialTheme.typography.titleMedium) }
                        Text(
                            stringResource(R.string.focus_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        Button(
                            onClick = { if (state.running) viewModel.pause() else viewModel.start() },
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("focus_toggle"),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                stringResource(if (state.running) R.string.focus_pause else R.string.focus_resume),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = viewModel::addFiveMinutes,
                                modifier = Modifier.weight(1f).height(52.dp).testTag("focus_plus_five"),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(stringResource(R.string.focus_add_five)) }
                            OutlinedButton(
                                onClick = viewModel::finishNow,
                                modifier = Modifier.weight(1f).height(52.dp).testTag("focus_finish"),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(stringResource(R.string.focus_finish)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDial(progress: Float, remaining: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f).testTag("focus_dial"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(remaining, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Finished(minutes: Int, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🎉", fontSize = 44.sp)
        Text(
            if (minutes > 0) pluralStringResource(R.plurals.focus_logged, minutes, minutes) else stringResource(R.string.focus_nothing_logged),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("focus_result"),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.action_ok))
        }
    }
}
