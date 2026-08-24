package com.joeabouserhal.financetracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** DataStore-backed app settings. Theme is the only setting in Phase 1. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_SPEC_ID = stringPreferencesKey("theme_spec_id")
  }

  val themeSelection: Flow<ThemeSelection> =
    dataStore.data
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs ->
        ThemeSelection(
          mode = prefs[Keys.THEME_MODE]?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM,
          specId = prefs[Keys.THEME_SPEC_ID],
        )
      }

  suspend fun setThemeSelection(selection: ThemeSelection) {
    dataStore.edit { prefs ->
      prefs[Keys.THEME_MODE] = selection.mode.name
      if (selection.specId != null) {
        prefs[Keys.THEME_SPEC_ID] = selection.specId
      } else {
        prefs.remove(Keys.THEME_SPEC_ID)
      }
    }
  }
}
