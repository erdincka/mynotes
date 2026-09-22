package uk.kayalab.mynotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.kayalab.mynotes.data.SettingsRepository
import uk.kayalab.mynotes.data.StylusButtonAction
import uk.kayalab.mynotes.data.StylusConfig
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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
