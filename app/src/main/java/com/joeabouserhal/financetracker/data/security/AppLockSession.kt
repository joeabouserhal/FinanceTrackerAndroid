package com.joeabouserhal.financetracker.data.security

enum class AppLockTiming { APP_RESTART, LEAVE_APP, AFTER_MINUTES }

data class AppLockConfig(
  val enabled: Boolean = false,
  val timing: AppLockTiming = AppLockTiming.LEAVE_APP,
  val minutes: Int = 5,
  val blockScreenCapture: Boolean = false,
) {
  init { require(minutes in 1..1440) }
}

data class AppLockState(val config: AppLockConfig, val locked: Boolean)

/** Unlock grants exist only in memory. A new process always starts locked when enabled. */
internal class AppLockSession(config: AppLockConfig) {
  var state = AppLockState(config, config.enabled)
    private set
  private var backgroundAt: Long? = null

  fun configure(config: AppLockConfig) {
    state = AppLockState(config, config.enabled && (state.locked || !state.config.enabled))
    if (!config.enabled) backgroundAt = null
  }

  fun unlock() {
    state = state.copy(locked = false)
    backgroundAt = null
  }

  fun lock() { if (state.config.enabled) state = state.copy(locked = true) }

  fun background(now: Long, sessionEnded: Boolean = false) {
    backgroundAt = now
    if (sessionEnded || state.config.timing == AppLockTiming.LEAVE_APP) lock()
  }

  fun foreground(now: Long) {
    val leftAt = backgroundAt
    if (state.config.enabled && leftAt != null) {
      when (state.config.timing) {
        AppLockTiming.APP_RESTART -> Unit
        AppLockTiming.LEAVE_APP -> lock()
        AppLockTiming.AFTER_MINUTES ->
          if (now < leftAt || now - leftAt >= state.config.minutes * 60_000L) lock()
      }
    }
    backgroundAt = null
  }
}
