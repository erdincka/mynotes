package uk.kayalab.mynotes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val defaultFontFamilyKey = stringPreferencesKey("default_font_family")
    private val exportFolderUriKey = stringPreferencesKey("export_folder_uri")
    private val stylusPrimaryKey = stringPreferencesKey("stylus_primary_action")
    private val stylusSecondaryKey = stringPreferencesKey("stylus_secondary_action")
    private val stylusOnlyKey = booleanPreferencesKey("stylus_only")
    private val defaultTemplateKey = stringPreferencesKey("default_template")
    private val handwritingSearchKey = booleanPreferencesKey("handwriting_search")

    val isDarkTheme: Flow<Boolean?> = context.dataStore.data.map { it[darkThemeKey] }

    val defaultFontFamily: Flow<String> =
        context.dataStore.data.map { it[defaultFontFamilyKey] ?: "Default" }

    val exportFolderUri: Flow<String?> = context.dataStore.data.map { it[exportFolderUriKey] }

    val handwritingSearch: Flow<Boolean> = context.dataStore.data.map { it[handwritingSearchKey] ?: false }

    val defaultTemplate: Flow<PageTemplate> =
        context.dataStore.data.map { PageTemplate.fromName(it[defaultTemplateKey]) }

    val stylusConfig: Flow<StylusConfig> = context.dataStore.data.map { prefs ->
        val defaults = StylusConfig()
        StylusConfig(
            primaryButton = StylusButtonAction.fromName(prefs[stylusPrimaryKey], defaults.primaryButton),
            secondaryButton = StylusButtonAction.fromName(prefs[stylusSecondaryKey], defaults.secondaryButton),
            stylusOnly = prefs[stylusOnlyKey] ?: defaults.stylusOnly
        )
    }

    suspend fun setDarkTheme(isDark: Boolean?) {
        context.dataStore.edit { prefs ->
            if (isDark == null) prefs.remove(darkThemeKey) else prefs[darkThemeKey] = isDark
        }
    }

    suspend fun setDefaultFontFamily(fontFamily: String) {
        context.dataStore.edit { it[defaultFontFamilyKey] = fontFamily }
    }

    suspend fun setExportFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(exportFolderUriKey) else prefs[exportFolderUriKey] = uri
        }
    }

    suspend fun setStylusPrimaryAction(action: StylusButtonAction) {
        context.dataStore.edit { it[stylusPrimaryKey] = action.name }
    }

    suspend fun setStylusSecondaryAction(action: StylusButtonAction) {
        context.dataStore.edit { it[stylusSecondaryKey] = action.name }
    }

    suspend fun setHandwritingSearch(enabled: Boolean) {
        context.dataStore.edit { it[handwritingSearchKey] = enabled }
    }

    suspend fun setDefaultTemplate(template: PageTemplate) {
        context.dataStore.edit { it[defaultTemplateKey] = template.name }
    }

    suspend fun setStylusOnly(enabled: Boolean) {
        context.dataStore.edit { it[stylusOnlyKey] = enabled }
    }
}
