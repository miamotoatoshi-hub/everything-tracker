package com.example.trackly.presentation.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.ui.components.ConfirmDialog
import java.time.LocalDate

/** Settings group "Your data": back up, restore and export to CSV, all through the system file picker. */
@Composable
internal fun DataSection(
    isPremium: Boolean,
    onMessage: (String) -> Unit,
    onUpgrade: (PremiumFeature) -> Unit,
) {
    val viewModel = appViewModel { BackupViewModel(it.backupRepository, it.documentStore, it.premiumPolicy) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnUpgrade by rememberUpdatedState(onUpgrade)
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.RequiresPremium -> currentOnUpgrade(event.feature)
                else -> currentOnMessage(message(context, event))
            }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.saveBackup(it.toString()) }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.saveCsv(it.toString()) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingRestore = it.toString() }
    }
    val today = remember { LocalDate.now() }

    SettingsGroup(stringResource(R.string.settings_data)) {
        SettingsRow(
            title = stringResource(R.string.settings_backup),
            subtitle = stringResource(R.string.settings_backup_desc),
            onClick = if (busy) null else ({ saveBackup.launch("trackly-backup-$today.json") }),
            modifier = Modifier.testTag("backup_export"),
        )
        SettingsRow(
            title = stringResource(R.string.settings_restore),
            subtitle = stringResource(R.string.settings_restore_desc),
            onClick = if (busy) null else ({ openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }),
            modifier = Modifier.testTag("backup_restore"),
        )
        SettingsRow(
            title = stringResource(R.string.settings_export_csv),
            subtitle = stringResource(if (isPremium) R.string.settings_export_csv_desc else R.string.settings_export_csv_premium),
            onClick = if (busy) null else ({ if (viewModel.canExportCsv()) saveCsv.launch("trackly-$today.csv") }),
            modifier = Modifier.testTag("export_csv"),
        )
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            message = stringResource(R.string.restore_confirm_message),
            confirmLabel = stringResource(R.string.restore_confirm_action),
            destructive = true,
            onConfirm = {
                pendingRestore = null
                viewModel.restore(uri)
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

private fun message(context: Context, event: BackupEvent): String = when (event) {
    BackupEvent.BackupSaved -> context.getString(R.string.backup_saved)
    BackupEvent.CsvSaved -> context.getString(R.string.csv_saved)
    is BackupEvent.Restored -> context.resources.getQuantityString(R.plurals.backup_restored, event.habits, event.habits)
    BackupEvent.RestoreInvalid -> context.getString(R.string.backup_invalid)
    BackupEvent.RestoreTooNew -> context.getString(R.string.backup_too_new)
    BackupEvent.Failed -> context.getString(R.string.backup_failed)
    is BackupEvent.RequiresPremium -> ""
}
