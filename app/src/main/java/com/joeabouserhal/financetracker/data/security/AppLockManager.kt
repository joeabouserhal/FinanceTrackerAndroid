package com.joeabouserhal.financetracker.data.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Device-only security preferences, separate from account/sync data and excluded from backup. */
class AppLockManager(application: Application) : Application.ActivityLifecycleCallbacks {
  private val preferences = application.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
  private val session = AppLockSession(readConfig())
  private val mutableState = MutableStateFlow(session.state)
  val state = mutableState.asStateFlow()
  private val visibleActivities = mutableSetOf<Activity>()
  private val writes = Mutex()

  init { application.registerActivityLifecycleCallbacks(this) }

  private fun readConfig(): AppLockConfig = try {
    AppLockConfig(
      enabled = preferences.getBoolean("enabled", false),
      timing = AppLockTiming.entries.firstOrNull { it.name == preferences.getString("timing", null) }
        ?: AppLockTiming.LEAVE_APP,
      minutes = preferences.getInt("minutes", 5).coerceIn(1, 1440),
      blockScreenCapture = preferences.getBoolean("block_screen_capture", false),
    )
  } catch (_: RuntimeException) {
    // Corrupt security preferences must not silently turn protection off.
    AppLockConfig(enabled = true)
  }

  /** Called only after the system's authentication success callback. */
  suspend fun saveAfterAuthentication(config: AppLockConfig) = writes.withLock {
    persist(config)
    session.configure(config)
    session.unlock()
    if (visibleActivities.isEmpty()) session.background(SystemClock.elapsedRealtime())
    publish()
  }

  suspend fun setOptions(timing: AppLockTiming, minutes: Int, blockScreenCapture: Boolean) = writes.withLock {
    check(!session.state.locked) { "Unlock the app before changing App Lock." }
    val config = session.state.config.copy(timing = timing, minutes = minutes, blockScreenCapture = blockScreenCapture)
    persist(config)
    // Re-check after disk IO in case the app was backgrounded while saving.
    session.configure(config)
    publish()
  }

  private suspend fun persist(config: AppLockConfig) = withContext(Dispatchers.IO) {
    check(preferences.edit().putBoolean("enabled", config.enabled)
      .putString("timing", config.timing.name).putInt("minutes", config.minutes)
      .putBoolean("block_screen_capture", config.blockScreenCapture).commit()) {
      "App Lock settings could not be saved. Please try again."
    }
  }

  fun authenticated() {
    session.unlock()
    // A delayed success callback must not erase time-away tracking while no app screen is visible.
    if (visibleActivities.isEmpty()) session.background(SystemClock.elapsedRealtime())
    publish()
  }
  private fun publish() { mutableState.value = session.state }

  override fun onActivityStarted(activity: Activity) {
    if (activity !is AppLockActivity) return
    if (visibleActivities.isEmpty()) session.foreground(SystemClock.elapsedRealtime())
    visibleActivities.add(activity)
    publish()
  }

  override fun onActivityStopped(activity: Activity) {
    if (!visibleActivities.remove(activity)) return
    if (visibleActivities.isEmpty() && !activity.isChangingConfigurations) {
      session.background(SystemClock.elapsedRealtime(), sessionEnded = activity.isFinishing)
      publish()
    }
  }

  override fun onActivityDestroyed(activity: Activity) { visibleActivities.remove(activity) }
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
