@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.trackly.presentation.habit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.HabitFormError
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.domain.model.TimeOfDay
import com.example.trackly.presentation.common.HabitIcons
import com.example.trackly.presentation.common.LocalAppContainer
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.descriptionRes
import com.example.trackly.presentation.common.labelRes
import com.example.trackly.presentation.common.needsNotificationPermission
import com.example.trackly.presentation.common.rememberDateFormats
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ConfirmDialog
import com.example.trackly.ui.components.DatePickerDialogCompat
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.SectionTitle
import com.example.trackly.ui.components.TimePickerDialog
import com.example.trackly.ui.theme.habitAccent
import com.example.trackly.ui.theme.TracklyTheme
import java.text.BreakIterator
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun HabitFormScreen(
    habitId: Long,
    onClose: () -> Unit,
    onSaved: (Long) -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel(key = "habit_form_$habitId") {
        HabitFormViewModel(
            habitId = habitId,
            habits = it.habitRepository,
            saveHabit = it.saveHabit,
            settings = it.settingsRepository,
            policy = it.premiumPolicy,
            todayProvider = it.todayProvider,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstDay by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    val policy by LocalAppContainer.current.premiumPolicy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)
    var showDiscard by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Save regardless of the answer: reminders are stored and start working once permission is granted.
        viewModel.save()
    }
    val onSaveClick = {
        val hasReminders = state.reminders.any { it.enabled }
        if (hasReminders && needsNotificationPermission(context)) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.save()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HabitFormEvent.Saved -> currentOnSaved(event.habitId)
                is HabitFormEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                HabitFormEvent.Failed -> Unit
            }
        }
    }

    val requestClose = {
        if (viewModel.isDirty) showDiscard = true else onClose()
    }
    BackHandler(enabled = true) { requestClose() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.form_title_edit else R.string.form_title_new))
                },
                navigationIcon = {
                    IconButton(onClick = requestClose, modifier = Modifier.testTag("form_close")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Button(
                    onClick = onSaveClick,
                    enabled = state.loaded && !state.notFound && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                        .testTag("save_habit"),
                ) {
                    Text(stringResource(R.string.form_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.loaded -> LoadingState()
                state.notFound -> ErrorState(
                    title = stringResource(R.string.habit_not_found_title),
                    message = stringResource(R.string.habit_not_found_message),
                )
                else -> CenteredContent(maxWidth = 640.dp) {
                    FormBody(
                        state = state,
                        firstDay = firstDay,
                        isPremium = policy.isPremium,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.form_discard_title),
            message = stringResource(R.string.form_discard_message),
            confirmLabel = stringResource(R.string.form_discard),
            destructive = true,
            onConfirm = {
                showDiscard = false
                onClose()
            },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun FormBody(
    state: HabitFormState,
    firstDay: DayOfWeek,
    isPremium: Boolean,
    viewModel: HabitFormViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        NameSection(state, viewModel)
        IconSection(state, viewModel)
        ColorSection(state, isPremium, viewModel)
        TypeSection(state, viewModel)
        if (state.type != HabitType.BOOLEAN) GoalSection(state, viewModel)
        FrequencySection(state, firstDay, viewModel)
        TimeOfDaySection(state, viewModel)
        RemindersSection(state, viewModel)
        StartDateSection(state, viewModel)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
    }
}

// region name + icon + colour

@Composable
private fun NameSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    val isDark = TracklyTheme.extras.isDark
    val accent = Color(state.colorArgb).habitAccent(isDark)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.padding(top = 4.dp).size(60.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.icon, fontSize = 30.sp)
        }
        val nameError = HabitFormError.NAME_EMPTY in state.errors || HabitFormError.NAME_TOO_LONG in state.errors
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            modifier = Modifier.weight(1f).testTag("habit_name_field"),
            singleLine = true,
            isError = nameError,
            label = { Text(stringResource(R.string.form_name)) },
            supportingText = {
                if (nameError) {
                    Text(
                        stringResource(
                            if (HabitFormError.NAME_TOO_LONG in state.errors) R.string.error_name_too_long else R.string.error_name_required,
                        ),
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
    }
}

private fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val end = iterator.next()
    return text.substring(0, if (end == BreakIterator.DONE) text.length else end)
}

@Composable
private fun IconSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    var custom by remember { mutableStateOf("") }
    FormSection(stringResource(R.string.form_icon)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitIcons.all.forEach { emoji ->
                val selected = emoji == state.icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                        )
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.onIconChange(emoji) })
                        .semantics { contentDescription = emoji }
                        .testTag("icon_$emoji"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = {
                custom = it.take(8)
                val emoji = firstGrapheme(custom.trim())
                if (emoji.isNotEmpty()) viewModel.onIconChange(emoji)
            },
            singleLine = true,
            label = { Text(stringResource(R.string.form_icon_custom)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun ColorSection(state: HabitFormState, isPremium: Boolean, viewModel: HabitFormViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val isDark = TracklyTheme.extras.isDark
    FormSection(stringResource(R.string.form_color)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitColors.FREE.forEach { argb -> ColorSwatch(argb, state.colorArgb == argb, locked = false, isDark) { viewModel.onColorChange(argb) } }
            HabitColors.PREMIUM.forEach { argb ->
                ColorSwatch(argb, state.colorArgb == argb, locked = !isPremium, isDark) { viewModel.onColorChange(argb) }
            }
            val customSelected = !HabitColors.FREE.contains(state.colorArgb) && !HabitColors.PREMIUM.contains(state.colorArgb)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (customSelected) Color(state.colorArgb) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClickLabel = stringResource(R.string.form_color_custom)) {
                        if (isPremium) showPicker = true else viewModel.onColorChange(0)
                    }
                    .testTag("color_custom"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPremium) Icons.Rounded.Palette else Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.form_color_custom),
                    tint = if (customSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (!isPremium) {
            Text(
                text = stringResource(R.string.form_color_premium_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showPicker) {
        CustomColorDialog(
            initial = Color(state.colorArgb),
            onConfirm = {
                viewModel.onColorChange(it.toArgb())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, locked: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val color = Color(argb)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.habitAccent(isDark))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag("color_${argb.toUInt().toString(16)}"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            locked -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CustomColorDialog(initial: Color, onConfirm: (Color) -> Unit, onDismiss: () -> Unit) {
    var hue by remember { mutableFloatStateOf(220f) }
    var saturation by remember { mutableFloatStateOf(0.7f) }
    var brightness by remember { mutableFloatStateOf(0.85f) }
    val color = Color.hsv(hue, saturation, brightness)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.form_color_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(color),
                )
                Text(stringResource(R.string.color_hue), style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(R.string.color_saturation), style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.3f..1f)
                Text(stringResource(R.string.color_brightness), style = MaterialTheme.typography.labelMedium)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.4f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// endregion

// region type + goal

@Composable
private fun TypeSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    FormSection(stringResource(R.string.form_type)) {
        HabitType.entries.forEach { type ->
            val selected = state.type == type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.onTypeChange(type) })
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("type_${type.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(type.labelRes()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(type.descriptionRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun GoalSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    FormSection(stringResource(R.string.form_goal)) {
        OutlinedTextField(
            value = state.goalText,
            onValueChange = viewModel::onGoalChange,
            modifier = Modifier.fillMaxWidth().testTag("goal_field"),
            singleLine = true,
            label = { Text(stringResource(R.string.form_goal_label)) },
            isError = HabitFormError.GOAL_INVALID in state.errors,
            supportingText = {
                if (HabitFormError.GOAL_INVALID in state.errors) Text(stringResource(R.string.error_goal_invalid))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp),
        )
        Text(stringResource(R.string.form_unit), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.type.allowedUnits.forEach { unit ->
                FilterChip(
                    selected = state.unit == unit,
                    onClick = { viewModel.onUnitChange(unit) },
                    label = { Text(stringResource(unit.labelRes())) },
                    modifier = Modifier.testTag("unit_${unit.name.lowercase()}"),
                )
            }
        }
        if (state.unit == HabitUnit.CUSTOM) {
            OutlinedTextField(
                value = state.customUnit,
                onValueChange = viewModel::onCustomUnitChange,
                modifier = Modifier.fillMaxWidth().testTag("custom_unit_field"),
                singleLine = true,
                label = { Text(stringResource(R.string.form_custom_unit)) },
                isError = HabitFormError.CUSTOM_UNIT_EMPTY in state.errors,
                supportingText = {
                    if (HabitFormError.CUSTOM_UNIT_EMPTY in state.errors) Text(stringResource(R.string.error_custom_unit))
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
        OutlinedTextField(
            value = state.stepText,
            onValueChange = viewModel::onStepChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.form_step)) },
            isError = HabitFormError.STEP_INVALID in state.errors,
            supportingText = {
                Text(
                    stringResource(
                        if (HabitFormError.STEP_INVALID in state.errors) R.string.error_step_invalid else R.string.form_step_help,
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

// endregion

// region schedule + time of day

@Composable
private fun FrequencySection(state: HabitFormState, firstDay: DayOfWeek, viewModel: HabitFormViewModel) {
    val dates = rememberDateFormats()
    FormSection(stringResource(R.string.form_frequency)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleType.entries.forEach { type ->
                FilterChip(
                    selected = state.scheduleType == type,
                    onClick = { viewModel.onScheduleTypeChange(type) },
                    label = { Text(stringResource(scheduleLabel(type))) },
                    modifier = Modifier.testTag("schedule_${type.name.lowercase()}"),
                )
            }
        }
        if (state.scheduleType == ScheduleType.SPECIFIC_DAYS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                List(7) { firstDay.plus(it.toLong()) }.forEach { day ->
                    val selected = day in state.days
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { viewModel.onToggleDay(day) })
                            .semantics { contentDescription = dates.weekdayFull(day) }
                            .testTag("day_${day.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dates.weekdayNarrow(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (HabitFormError.NO_DAYS_SELECTED in state.errors) {
                Text(
                    stringResource(R.string.error_no_days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state.scheduleType == ScheduleType.EVERY_N_DAYS) {
            OutlinedTextField(
                value = state.intervalText,
                onValueChange = viewModel::onIntervalChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.form_interval_label)) },
                isError = HabitFormError.INTERVAL_INVALID in state.errors,
                supportingText = {
                    Text(
                        stringResource(
                            if (HabitFormError.INTERVAL_INVALID in state.errors) R.string.error_interval_invalid else R.string.form_interval_help,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

private fun scheduleLabel(type: ScheduleType): Int = when (type) {
    ScheduleType.EVERY_DAY -> R.string.schedule_every_day
    ScheduleType.WEEKDAYS -> R.string.schedule_weekdays
    ScheduleType.WEEKENDS -> R.string.schedule_weekends
    ScheduleType.SPECIFIC_DAYS -> R.string.schedule_specific_days
    ScheduleType.EVERY_N_DAYS -> R.string.schedule_every_n_days
}

fun timeOfDayLabel(value: TimeOfDay): Int = when (value) {
    TimeOfDay.ANYTIME -> R.string.tod_anytime
    TimeOfDay.MORNING -> R.string.tod_morning
    TimeOfDay.AFTERNOON -> R.string.tod_afternoon
    TimeOfDay.EVENING -> R.string.tod_evening
    TimeOfDay.CUSTOM -> R.string.tod_custom
}

@Composable
private fun TimeOfDaySection(state: HabitFormState, viewModel: HabitFormViewModel) {
    FormSection(stringResource(R.string.form_time_of_day)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeOfDay.entries.forEach { value ->
                FilterChip(
                    selected = state.timeOfDay == value,
                    onClick = { viewModel.onTimeOfDayChange(value) },
                    label = { Text(stringResource(timeOfDayLabel(value))) },
                )
            }
        }
    }
}

// endregion

// region reminders

@Composable
private fun RemindersSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    val dates = rememberDateFormats()
    var editing by remember { mutableStateOf<ReminderDraft?>(null) }
    var adding by remember { mutableStateOf(false) }

    FormSection(stringResource(R.string.form_reminders)) {
        if (state.reminders.isEmpty()) {
            Text(
                stringResource(R.string.form_reminders_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.reminders.forEach { reminder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = stringResource(R.string.reminder_edit)) { editing = reminder }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dates.time(reminder.time), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = reminder.message.ifBlank { stringResource(R.string.reminder_default_message_label) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { viewModel.setReminderEnabled(reminder.key, it) },
                    modifier = Modifier.semantics { contentDescription = dates.time(reminder.time) },
                )
                IconButton(onClick = { viewModel.removeReminder(reminder.key) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.reminder_delete))
                }
            }
        }
        OutlinedButton(
            onClick = { if (viewModel.canAddReminder()) adding = true },
            modifier = Modifier.testTag("add_reminder"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.reminder_add))
        }
    }

    if (adding) {
        ReminderDialog(
            initial = null,
            defaultTime = viewModel.suggestedReminderTime(),
            onConfirm = { time, message, everyDay ->
                viewModel.addReminder(time, message, everyDay)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { reminder ->
        ReminderDialog(
            initial = reminder,
            defaultTime = reminder.time,
            onConfirm = { time, message, everyDay ->
                viewModel.updateReminder(reminder.key, time, message, everyDay)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ReminderDialog(
    initial: ReminderDraft?,
    defaultTime: LocalTime,
    onConfirm: (LocalTime, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val dates = rememberDateFormats()
    var time by remember { mutableStateOf(initial?.time ?: defaultTime) }
    var message by remember { mutableStateOf(initial?.message.orEmpty()) }
    var everyDay by remember { mutableStateOf(initial?.everyDay ?: false) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.reminder_add else R.string.reminder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().testTag("reminder_time")) {
                    Text(dates.time(time), style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(100) },
                    label = { Text(stringResource(R.string.reminder_message_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("reminder_message"),
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = everyDay, role = Role.Switch, onValueChange = { everyDay = it }),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminder_every_day), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.reminder_every_day_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = everyDay, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(time, message, everyDay) }, modifier = Modifier.testTag("reminder_confirm")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickTime) {
        TimePickerDialog(
            initial = time,
            onConfirm = {
                time = it
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

// endregion

@Composable
private fun StartDateSection(state: HabitFormState, viewModel: HabitFormViewModel) {
    val dates = rememberDateFormats()
    var show by remember { mutableStateOf(false) }
    FormSection(stringResource(R.string.form_start_date)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dates.fullDate(state.startDate), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.form_start_date_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { show = true }) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.form_start_date_change))
            }
        }
    }
    if (show) {
        DatePickerDialogCompat(
            initial = state.startDate,
            onConfirm = {
                viewModel.onStartDateChange(it)
                show = false
            },
            onDismiss = { show = false },
        )
    }
}
