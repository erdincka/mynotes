package uk.kayalab.mynotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import uk.kayalab.mynotes.data.FolderRepository
import uk.kayalab.mynotes.data.SettingsRepository
import uk.kayalab.mynotes.export.BackupService
import uk.kayalab.mynotes.data.StylusButtonAction
import uk.kayalab.mynotes.data.StylusConfig
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupService: BackupService,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun backupFileName(): String = backupService.suggestedFileName()

    fun backupTo(uri: Uri) = runBusy {
        backupService.writeBackup(uri, folderRepository.allFolders.first())
            .onSuccess { _message.value = if (it == 1) "Backed up 1 note." else "Backed up $it notes." }
            .onFailure { _message.value = "Backup failed: ${it.message}" }
    }

    fun restoreFrom(uri: Uri) = runBusy {
        backupService.restoreBackup(uri)
            .onSuccess { _message.value = "Restored ${it.notes} notes and ${it.folders} folders." }
            .onFailure { _message.value = "Restore failed: ${it.message}" }
    }

    fun consumeMessage() { _message.value = null }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try { runCatching { block() }.onFailure { Timber.e(it); _message.value = "Something went wrong: ${it.message}" } }
            finally { _busy.value = false }
        }
    }

    val isDarkTheme: StateFlow<Boolean?> = settingsRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val defaultFontFamily: StateFlow<String> = settingsRepository.defaultFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default")

    val exportFolderUri: StateFlow<String?> = settingsRepository.exportFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stylusConfig: StateFlow<StylusConfig> = settingsRepository.stylusConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StylusConfig())

    fun setDarkTheme(isDark: Boolean?) = update { settingsRepository.setDarkTheme(isDark) }
    fun setDefaultFontFamily(fontFamily: String) = update { settingsRepository.setDefaultFontFamily(fontFamily) }
    fun setExportFolderUri(uri: String?) = update { settingsRepository.setExportFolderUri(uri) }
    fun setStylusPrimaryAction(action: StylusButtonAction) = update { settingsRepository.setStylusPrimaryAction(action) }
    fun setStylusSecondaryAction(action: StylusButtonAction) = update { settingsRepository.setStylusSecondaryAction(action) }
    fun setStylusOnly(enabled: Boolean) = update { settingsRepository.setStylusOnly(enabled) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { Timber.e(it, "Settings update failed") }
        }
    }
}
