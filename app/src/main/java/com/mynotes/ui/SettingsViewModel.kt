package com.mynotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mynotes.data.SettingsRepository
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
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val oneDriveClient: OneDriveClient,
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

    private val _oneDriveFolders = MutableStateFlow<List<OneDriveItem>>(emptyList())
    val oneDriveFolders = _oneDriveFolders.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

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

    fun connectOneDrive(token: String) {
        viewModelScope.launch {
            _isConnecting.value = true
            // Verify token by listing root children
            val result = oneDriveClient.listFolderItems(token, "root")
            if (result.isSuccess) {
                settingsRepository.setOneDriveConfig(token, null, null)
                _oneDriveFolders.value = result.getOrNull()?.filter { it.size == null } ?: emptyList()
            }
            _isConnecting.value = false
        }
    }

    fun getOAuthUrl(): String {
        val clientId = "9c07213f-db1e-44d4-94dd-662e04efaf85" // Placeholder
        val redirectUri = "mynotes://onedrive-auth"
        val scope = "files.readwrite offline_access"
        return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
                "client_id=$clientId" +
                "&response_type=token" +
                "&redirect_uri=$redirectUri" +
                "&scope=$scope"
    }

    fun handleAuthRedirect(uri: android.net.Uri) {
        val fragment = uri.fragment
        if (fragment != null) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                parts[0] to parts.getOrElse(1) { "" }
            }
            val accessToken = params["access_token"]
            if (accessToken != null) {
                connectOneDrive(accessToken)
            }
        }
    }

    fun selectOneDriveFolder(folder: OneDriveItem) {
        viewModelScope.launch {
            val token = onedriveAccessToken.value ?: return@launch
            settingsRepository.setOneDriveConfig(token, folder.id, folder.name)
            syncScheduler.scheduleSync(immediate = true)
        }
    }

    fun disconnectOneDrive() {
        viewModelScope.launch {
            settingsRepository.setOneDriveConfig(null, null, null)
            _oneDriveFolders.value = emptyList()
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            val token = onedriveAccessToken.value ?: return@launch
            val result = oneDriveClient.listFolderItems(token, "root")
            if (result.isSuccess) {
                _oneDriveFolders.value = result.getOrNull()?.filter { it.size == null } ?: emptyList()
            }
        }
    }
}
