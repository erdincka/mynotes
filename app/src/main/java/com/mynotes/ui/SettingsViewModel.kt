package com.mynotes.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mynotes.data.SettingsRepository
import com.mynotes.sync.OneDriveAuthManager
import com.mynotes.sync.OneDriveClient
import com.mynotes.sync.OneDriveItem
import com.mynotes.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val oneDriveClient: OneDriveClient,
    private val oneDriveAuthManager: OneDriveAuthManager,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean?> = settingsRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val defaultFontFamily: StateFlow<String> = settingsRepository.defaultFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default")

    val onedriveAccessToken: StateFlow<String?> = settingsRepository.onedriveAccessToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val onedriveFolderName: StateFlow<String?> = settingsRepository.onedriveFolderName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val exportFolderUri: StateFlow<String?> = settingsRepository.exportFolderUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _oneDriveFolders = MutableStateFlow<List<OneDriveItem>>(emptyList())
    val oneDriveFolders = _oneDriveFolders.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

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

    /** Launches the MSAL interactive sign-in flow via Chrome Custom Tab. */
    fun signInOneDrive(activity: ComponentActivity) {
        viewModelScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null
            val tokenResult = oneDriveAuthManager.signIn(activity)
            tokenResult.onSuccess { token ->
                // Store a connection marker; MSAL caches the account internally
                settingsRepository.setOneDriveConfig("msal_connected", null, null)
                fetchOneDriveFolders(token)
            }.onFailure { e ->
                Timber.e(e, "SettingsViewModel: OneDrive sign-in failed")
                _errorMessage.value = e.message ?: "Sign-in failed"
            }
            _isConnecting.value = false
        }
    }

    fun selectOneDriveFolder(folder: OneDriveItem) {
        viewModelScope.launch {
            settingsRepository.setOneDriveConfig("msal_connected", folder.id, folder.name)
            syncScheduler.scheduleSync(immediate = true)
            _oneDriveFolders.value = emptyList()
        }
    }

    fun disconnectOneDrive() {
        viewModelScope.launch {
            oneDriveAuthManager.signOut()
            settingsRepository.setOneDriveConfig(null, null, null)
            _oneDriveFolders.value = emptyList()
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            _isConnecting.value = true
            val tokenResult = oneDriveAuthManager.silentSignIn()
            tokenResult.onSuccess { token ->
                fetchOneDriveFolders(token)
            }.onFailure { e ->
                Timber.e(e, "SettingsViewModel: token refresh failed")
                _errorMessage.value = "Session expired — please sign in again"
            }
            _isConnecting.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun fetchOneDriveFolders(token: String) {
        val result = oneDriveClient.listFolderItems(token, "root")
        result.onSuccess { items ->
            _oneDriveFolders.value = items.filter { it.size == null }
        }.onFailure { e ->
            Timber.e(e, "SettingsViewModel: failed to list OneDrive folders")
            _errorMessage.value = "Could not list OneDrive folders: ${e.message}"
        }
    }
}
