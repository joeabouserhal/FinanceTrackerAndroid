package com.joeabouserhal.financetracker.data.security

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLockManagerTest {
  private val app get() = ApplicationProvider.getApplicationContext<Application>()
  @Before fun clearSyntheticSettings() { app.getSharedPreferences("app_lock", 0).edit().clear().commit() }

  @Test fun configPersistsButUnlockDoesNot() = runBlocking {
    val manager = AppLockManager(app)
    val expected = AppLockConfig(true, AppLockTiming.AFTER_MINUTES, 17, blockScreenCapture = true)
    manager.saveAfterAuthentication(expected)
    val reopened = AppLockManager(app)
    assertEquals(expected, reopened.state.value.config)
    assertTrue(reopened.state.value.locked)
  }

  @Test fun disablingSurvivesProcessRestart() = runBlocking {
    val manager = AppLockManager(app)
    manager.saveAfterAuthentication(AppLockConfig(enabled = true))
    manager.saveAfterAuthentication(AppLockConfig(enabled = false))
    assertFalse(AppLockManager(app).state.value.locked)
    assertFalse(AppLockManager(app).state.value.config.enabled)
  }

  @Test fun corruptPreferencesFailClosed() {
    app.getSharedPreferences("app_lock", 0).edit().putString("enabled", "invalid").commit()
    assertTrue(AppLockManager(app).state.value.locked)
  }

  @Test fun screenProtectionPersistsWithoutEnablingAppLock() = runBlocking {
    val manager = AppLockManager(app)
    manager.setOptions(AppLockTiming.LEAVE_APP, 5, blockScreenCapture = true)
    val restored = AppLockManager(app).state.value
    assertFalse(restored.config.enabled)
    assertFalse(restored.locked)
    assertTrue(restored.config.blockScreenCapture)
  }

  @Test fun unknownModeUsesImmediateLock() {
    app.getSharedPreferences("app_lock", 0).edit().putBoolean("enabled", true).putString("timing", "unknown").commit()
    assertEquals(AppLockTiming.LEAVE_APP, AppLockManager(app).state.value.config.timing)
  }

  @Test fun timingCannotBeChangedWhileLocked() {
    app.getSharedPreferences("app_lock", 0).edit().putBoolean("enabled", true).commit()
    assertThrows(IllegalStateException::class.java) {
      runBlocking { AppLockManager(app).setOptions(AppLockTiming.APP_RESTART, 5, false) }
    }
  }
}
