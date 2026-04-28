package com.mynotes.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val DEFAULT_FONT_FAMILY_KEY = stringPreferencesKey("default_font_family")
    private val ONEDRIVE_ACCESS_TOKEN_KEY = stringPreferencesKey("onedrive_access_token")
    private val ONEDRIVE_FOLDER_ID_KEY = stringPreferencesKey("onedrive_folder_id")
    private val ONEDRIVE_FOLDER_NAME_KEY = stringPreferencesKey("onedrive_folder_name")
    private val EXPORT_FOLDER_URI_KEY = stringPreferencesKey("export_folder_uri")

    val isDarkTheme: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY]
    }

    val defaultFontFamily: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_FONT_FAMILY_KEY] ?: "Default"
    }

    val onedriveAccessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ONEDRIVE_ACCESS_TOKEN_KEY]
    }

    val onedriveFolderId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ONEDRIVE_FOLDER_ID_KEY]
    }

    val onedriveFolderName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ONEDRIVE_FOLDER_NAME_KEY]
    }

    val exportFolderUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[EXPORT_FOLDER_URI_KEY]
    }

    suspend fun getExportDirectory(): File {
        val exportFolderUri = exportFolderUri.first()
        return if (exportFolderUri != null && exportFolderUri.isNotBlank()) {
            val uri = Uri.parse(exportFolderUri)
            if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!)
            } else {
                context.filesDir
            }
        } else {
            context.filesDir
        }
    }

    suspend fun setDarkTheme(isDark: Boolean?) {
        context.dataStore.edit { preferences ->
            if (isDark == null) {
                preferences.remove(DARK_THEME_KEY)
            } else {
                preferences[DARK_THEME_KEY] = isDark
            }
        }
    }

    suspend fun setDefaultFontFamily(fontFamily: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_FONT_FAMILY_KEY] = fontFamily
        }
    }

    suspend fun setOneDriveConfig(accessToken: String?, folderId: String?, folderName: String?) {
        context.dataStore.edit { preferences ->
            if (accessToken == null) preferences.remove(ONEDRIVE_ACCESS_TOKEN_KEY)
            else preferences[ONEDRIVE_ACCESS_TOKEN_KEY] = accessToken

            if (folderId == null) preferences.remove(ONEDRIVE_FOLDER_ID_KEY)
            else preferences[ONEDRIVE_FOLDER_ID_KEY] = folderId

            if (folderName == null) preferences.remove(ONEDRIVE_FOLDER_NAME_KEY)
            else preferences[ONEDRIVE_FOLDER_NAME_KEY] = folderName
        }
    }

    suspend fun setExportFolderUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) preferences.remove(EXPORT_FOLDER_URI_KEY)
            else preferences[EXPORT_FOLDER_URI_KEY] = uri
        }
    }
}
