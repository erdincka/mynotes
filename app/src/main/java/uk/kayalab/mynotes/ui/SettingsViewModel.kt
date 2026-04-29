package uk.kayalab.mynotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import uk.kayalab.mynotes.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setDarkTheme(isDark: Boolean?) {
        viewModelScope.launch {
            settingsRepository.setDarkTheme(isDark)
        }
    }

    fun setDefaultFontFamily(fontFamily: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultFontFamily(fontFamily)
        }
    }

    fun setExportFolderUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setExportFolderUri(uri)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
