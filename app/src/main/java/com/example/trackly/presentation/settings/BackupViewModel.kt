package com.example.trackly.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.BackupImportResult
import com.example.trackly.domain.repository.BackupRepository
import com.example.trackly.domain.repository.DocumentStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupEvent {
    data object BackupSaved : BackupEvent
    data object CsvSaved : BackupEvent
    data class Restored(val habits: Int, val entries: Int) : BackupEvent
    data object RestoreInvalid : BackupEvent
    data object RestoreTooNew : BackupEvent
    data object Failed : BackupEvent
    data class RequiresPremium(val feature: PremiumFeature) : BackupEvent
}

/** Back up / restore (free: it is the user's data) and CSV export (Premium). */
class BackupViewModel(
    private val backup: BackupRepository,
    private val documents: DocumentStore,
    private val policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    /** Call before opening the file picker for a CSV export; false means the paywall was requested instead. */
    fun canExportCsv(): Boolean {
        val allowed = policy.value.isUnlocked(PremiumFeature.DATA_EXPORT)
        if (!allowed) _events.tryEmit(BackupEvent.RequiresPremium(PremiumFeature.DATA_EXPORT))
        return allowed
    }

    fun saveBackup(uri: String) = run {
        documents.write(uri, backup.exportBackup())
        BackupEvent.BackupSaved
    }

    fun saveCsv(uri: String) {
        if (!policy.value.isUnlocked(PremiumFeature.DATA_EXPORT)) {
            _events.tryEmit(BackupEvent.RequiresPremium(PremiumFeature.DATA_EXPORT))
            return
        }
        run {
            documents.write(uri, backup.exportCsv())
            BackupEvent.CsvSaved
        }
    }

    fun restore(uri: String) = run {
        when (val result = backup.importBackup(documents.read(uri))) {
            is BackupImportResult.Success -> BackupEvent.Restored(result.habits, result.entries)
            BackupImportResult.Invalid -> BackupEvent.RestoreInvalid
            BackupImportResult.TooNew -> BackupEvent.RestoreTooNew
        }
    }

    private fun run(block: suspend () -> BackupEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val event = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("Backup", "Backup action failed", error)
                BackupEvent.Failed
            } finally {
                _busy.value = false
            }
            _events.emit(event)
        }
    }
}
