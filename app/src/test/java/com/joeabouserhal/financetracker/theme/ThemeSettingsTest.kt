package com.joeabouserhal.financetracker.theme

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.joeabouserhal.financetracker.data.settings.SettingsRepository
import com.joeabouserhal.financetracker.data.settings.ThemeMode
import com.joeabouserhal.financetracker.data.settings.ThemeSelection
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ThemeSettingsTest {
  @Test
  fun `community choice survives reopening local settings and system clears custom id`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = context.preferencesDataStoreFile("themes-${UUID.randomUUID()}")
    try {
      for (theme in ThemeCatalog.community) {
        val selection = ThemeSelection(ThemeMode.CUSTOM, theme.id)
        val writeJob = SupervisorJob()
        try {
          val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + writeJob)) { file }
          SettingsRepository(store).setThemeSelection(selection)
        } finally {
          writeJob.cancelAndJoin()
        }
        val readJob = SupervisorJob()
        try {
          val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + readJob)) { file }
          val settings = SettingsRepository(store)
          assertEquals(selection, settings.themeSelection.first())
          settings.setThemeSelection(ThemeSelection())
          assertEquals(ThemeSelection(), settings.themeSelection.first())
        } finally {
          readJob.cancelAndJoin()
        }
      }
    } finally {
      file.delete()
    }
  }
}
