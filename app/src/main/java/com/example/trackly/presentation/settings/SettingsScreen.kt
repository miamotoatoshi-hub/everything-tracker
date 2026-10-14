@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.trackly.presentation.settings

import android.content.Context
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.BuildConfig
import com.example.trackly.R
import com.example.trackly.billing.BillingConfig
import com.example.trackly.billing.BillingEvent
import com.example.trackly.core.locale.AppLocales
import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.domain.model.UnitSystem
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.billingMessage
import com.example.trackly.presentation.common.needsNotificationPermission
import com.example.trackly.presentation.common.openAppNotificationSettings
import com.example.trackly.presentation.common.openUrl
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState
import com.example.trackly.ui.components.SegmentedControl
import com.example.trackly.ui.theme.swatchColor
import com.example.trackly.debug.DebugTools
import com.example.trackly.presentation.common.LocalAppContainer

enum class LegalKind { PRIVACY, TERMS }

@Composable
fun SettingsScreen(
    onOpenPremium: () -> Unit,
    onOpenLegal: (LegalKind) -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel {
        SettingsViewModel(it.settingsRepository, it.billingRepository, it.isPremium, it.reminderScheduler)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)

    LaunchedEffect(viewModel) {
        viewModel.billingEvents.collect { event ->
            val message = billingMessage(context, event)
            if (message != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                SettingsEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.error_action_failed))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(onRetry = viewModel::retry)
            is UiState.Content -> CenteredContent(maxWidth = 640.dp) {
                SettingsBody(current.data, viewModel, onOpenPremium, onOpenLegal)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun SettingsBody(
    data: SettingsData,
    viewModel: SettingsViewModel,
    onOpenPremium: () -> Unit,
    onOpenLegal: (LegalKind) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val settings = data.settings
    val context = LocalContext.current

    var showFirstDay by remember { mutableStateOf(false) }
    var showUnits by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var notificationsBlocked by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsBlocked = settings.notificationsEnabled && needsNotificationPermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = top + 12.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
            .testTag("settings_body"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)

        // Premium first: it is the one section that changes what the user can do.
        SettingsGroup(stringResource(R.string.settings_premium)) {
            PremiumStatusRow(data, onOpenPremium)
            if (data.entitlement.status != EntitlementStatus.NONE || data.isPremium) {
                SettingsRow(
                    title = stringResource(R.string.settings_manage_subscription),
                    onClick = { openUrl(context, BillingConfig.manageSubscriptionUrl(context.packageName)) },
                    modifier = Modifier.testTag("manage_subscription"),
                )
            }
            SettingsRow(
                title = stringResource(R.string.settings_restore_purchases),
                onClick = viewModel::restorePurchases,
                modifier = Modifier.testTag("restore_purchases"),
            )
        }

        SettingsGroup(stringResource(R.string.settings_appearance)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                ThemeModeChoice(settings.themeMode, viewModel::setTheme)
                Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleSmall)
                AccentPicker(settings.accent, data.isPremium) { viewModel.setAccent(it, data.isPremium) }
            }
        }

        SettingsGroup(stringResource(R.string.settings_notifications)) {
            SwitchRow(
                title = stringResource(R.string.settings_enable_notifications),
                subtitle = stringResource(R.string.settings_enable_notifications_desc),
                checked = settings.notificationsEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setNotificationsEnabled(enabled)
                    if (enabled && needsNotificationPermission(context)) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.testTag("switch_notifications"),
            )
            if (notificationsBlocked) {
                SettingsRow(
                    title = stringResource(R.string.settings_notifications_blocked),
                    subtitle = stringResource(R.string.settings_notifications_blocked_desc),
                    onClick = { openAppNotificationSettings(context) },
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_sound),
                checked = settings.soundEnabled,
                enabled = settings.notificationsEnabled,
                onCheckedChange = viewModel::setSound,
            )
            SwitchRow(
                title = stringResource(R.string.settings_vibration),
                checked = settings.vibrationEnabled,
                enabled = settings.notificationsEnabled,
                onCheckedChange = viewModel::setVibration,
            )
            SettingsRow(
                title = stringResource(R.string.settings_system_notifications),
                onClick = { openAppNotificationSettings(context) },
            )
        }

        SettingsGroup(stringResource(R.string.settings_general)) {
            SettingsRow(
                title = stringResource(R.string.settings_first_day),
                subtitle = stringResource(firstDayLabel(settings.firstDayOfWeek)),
                onClick = { showFirstDay = true },
                modifier = Modifier.testTag("setting_first_day"),
            )
            SettingsRow(
                title = stringResource(R.string.settings_units),
                subtitle = stringResource(unitsLabel(settings.unitSystem)),
                onClick = { showUnits = true },
            )
            SettingsRow(
                title = stringResource(R.string.settings_language),
                subtitle = AppLocales.currentTag()?.let { AppLocales.displayName(it) }
                    ?: stringResource(R.string.settings_language_system),
                onClick = { showLanguage = true },
            )
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            SettingsRow(
                title = stringResource(R.string.settings_privacy_policy),
                onClick = { openLegal(context, BuildConfig.PRIVACY_POLICY_URL) { onOpenLegal(LegalKind.PRIVACY) } },
                modifier = Modifier.testTag("privacy_policy"),
            )
            SettingsRow(
                title = stringResource(R.string.settings_terms),
                onClick = { openLegal(context, BuildConfig.TERMS_OF_SERVICE_URL) { onOpenLegal(LegalKind.TERMS) } },
                modifier = Modifier.testTag("terms_of_service"),
            )
            SettingsRow(
                title = stringResource(R.string.settings_version),
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                onClick = null,
            )
        }

        DebugTools.SettingsSection(LocalAppContainer.current)
    }

    if (showFirstDay) {
        ChoiceDialog(
            title = stringResource(R.string.settings_first_day),
            options = FirstDayOfWeekSetting.entries.toList(),
            selected = settings.firstDayOfWeek,
            label = { stringResource(firstDayLabel(it)) },
            onSelect = {
                viewModel.setFirstDay(it)
                showFirstDay = false
            },
            onDismiss = { showFirstDay = false },
        )
    }
    if (showUnits) {
        ChoiceDialog(
            title = stringResource(R.string.settings_units),
            options = UnitSystem.entries.toList(),
            selected = settings.unitSystem,
            label = { stringResource(unitsLabel(it)) },
            onSelect = {
                viewModel.setUnits(it)
                showUnits = false
            },
            onDismiss = { showUnits = false },
        )
    }
    if (showLanguage) {
        val tags: List<String?> = listOf<String?>(null) + AppLocales.supportedTags
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = tags,
            selected = AppLocales.currentTag(),
            label = { tag -> if (tag == null) stringResource(R.string.settings_language_system) else AppLocales.displayName(tag) },
            onSelect = {
                showLanguage = false
                AppLocales.apply(it)
            },
            onDismiss = { showLanguage = false },
        )
    }
}

private fun openLegal(context: Context, url: String, fallback: () -> Unit) {
    if (url.isBlank() || !openUrl(context, url)) fallback()
}

private fun firstDayLabel(value: FirstDayOfWeekSetting): Int = when (value) {
    FirstDayOfWeekSetting.SYSTEM -> R.string.first_day_system
    FirstDayOfWeekSetting.MONDAY -> R.string.first_day_monday
    FirstDayOfWeekSetting.SATURDAY -> R.string.first_day_saturday
    FirstDayOfWeekSetting.SUNDAY -> R.string.first_day_sunday
}

private fun unitsLabel(value: UnitSystem): Int = when (value) {
    UnitSystem.METRIC -> R.string.units_metric
    UnitSystem.IMPERIAL -> R.string.units_imperial
}

// region building blocks

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = checked, enabled = enabled, role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun PremiumStatusRow(data: SettingsData, onOpenPremium: () -> Unit) {
    val status = data.entitlement.status
    val title = when {
        data.isPremium && status == EntitlementStatus.ACTIVE_CANCELED -> R.string.settings_premium_canceled
        data.isPremium -> R.string.settings_premium_active
        status == EntitlementStatus.PENDING -> R.string.settings_premium_pending
        status == EntitlementStatus.EXPIRED -> R.string.settings_premium_expired
        else -> R.string.settings_premium_free
    }
    val subtitle = if (data.isPremium) R.string.settings_premium_active_desc else R.string.settings_premium_upgrade_desc
    SettingsRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onClick = onOpenPremium,
        modifier = Modifier.testTag("premium_status"),
    )
}

@Composable
private fun ThemeModeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    SegmentedControl(
        options = ThemeMode.entries.map { labels.getValue(it) },
        selectedIndex = ThemeMode.entries.indexOf(selected),
        onSelect = { onSelect(ThemeMode.entries[it]) },
        modifier = Modifier.testTag("theme_mode"),
    )
}

@Composable
private fun AccentPicker(selected: AccentTheme, isPremium: Boolean, onSelect: (AccentTheme) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentTheme.entries.forEach { accent ->
            val locked = accent.requiresPremium && !isPremium
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.swatchColor(), CircleShape)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .testTag("accent_${accent.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isSelected -> Icon(Icons.Rounded.Check, contentDescription = accent.name, tint = Color.White)
                    locked -> Icon(
                        Icons.Rounded.Lock,
                        contentDescription = accent.name,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// endregion
