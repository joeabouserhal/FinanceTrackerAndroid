package com.joeabouserhal.financetracker.data.security

import org.junit.Assert.*
import org.junit.Test

class AppLockSessionTest {
  private fun session(timing: AppLockTiming, minutes: Int = 5) =
    AppLockSession(AppLockConfig(true, timing, minutes))

  @Test fun disabledDoesNotLock() {
    val s = AppLockSession(AppLockConfig())
    s.background(0, sessionEnded = true); s.foreground(Long.MAX_VALUE)
    assertFalse(s.state.locked)
  }

  @Test fun everyEnabledModeStartsLocked() {
    AppLockTiming.entries.forEach { assertTrue(session(it).state.locked) }
  }

  @Test fun failedOrCancelledAuthenticationCannotUnlock() {
    val s = session(AppLockTiming.LEAVE_APP)
    s.background(0); s.foreground(1)
    assertTrue(s.state.locked) // Only an explicit success calls unlock().
  }

  @Test fun immediateLocksOnBackground() {
    val s = session(AppLockTiming.LEAVE_APP); s.unlock()
    s.background(100)
    assertTrue(s.state.locked)
    s.foreground(100)
    assertTrue(s.state.locked)
  }

  @Test fun restartOnlySurvivesSwitchingApps() {
    val s = session(AppLockTiming.APP_RESTART); s.unlock()
    s.background(0); s.foreground(86_400_000)
    assertFalse(s.state.locked)
  }

  @Test fun closingSessionLocksRestartOnly() {
    val s = session(AppLockTiming.APP_RESTART); s.unlock()
    s.background(100, sessionEnded = true)
    assertTrue(s.state.locked)
  }

  @Test fun timeoutUsesExactMonotonicBoundary() {
    val s = session(AppLockTiming.AFTER_MINUTES); s.unlock()
    s.background(1_000); s.foreground(300_999)
    assertFalse(s.state.locked)
    s.background(400_000); s.foreground(700_000)
    assertTrue(s.state.locked)
  }

  @Test fun timeActivelyUsingAppDoesNotCount() {
    val s = session(AppLockTiming.AFTER_MINUTES, 1); s.unlock()
    s.foreground(1_000_000)
    assertFalse(s.state.locked)
  }

  @Test fun shortVisitsResetBackgroundTimeout() {
    val s = session(AppLockTiming.AFTER_MINUTES, 1); s.unlock()
    repeat(5) { s.background(it * 60_000L); s.foreground(it * 60_000L + 59_999) }
    assertFalse(s.state.locked)
  }

  @Test fun clockResetFailsClosed() {
    val s = session(AppLockTiming.AFTER_MINUTES); s.unlock()
    s.background(500); s.foreground(100)
    assertTrue(s.state.locked)
  }

  @Test fun enablingRequiresASeparateSuccessfulUnlock() {
    val s = AppLockSession(AppLockConfig())
    s.configure(AppLockConfig(enabled = true))
    assertTrue(s.state.locked)
    s.unlock(); assertFalse(s.state.locked)
  }

  @Test fun timingChangesNeverUnlockAnAlreadyLockedSession() {
    val s = session(AppLockTiming.LEAVE_APP)
    s.configure(s.state.config.copy(timing = AppLockTiming.APP_RESTART))
    assertTrue(s.state.locked)
  }

  @Test fun disablingClearsLockAndOldTimeout() {
    val s = session(AppLockTiming.AFTER_MINUTES); s.background(0)
    s.configure(s.state.config.copy(enabled = false))
    s.foreground(1_000_000)
    assertFalse(s.state.locked)
  }

  @Test fun freshSessionNeverInheritsUnlockGrant() {
    val original = session(AppLockTiming.APP_RESTART); original.unlock()
    assertTrue(AppLockSession(original.state.config).state.locked)
  }

  @Test fun invalidTimeoutsAreRejected() {
    listOf(0, -1, 1441, Int.MAX_VALUE).forEach {
      assertThrows(IllegalArgumentException::class.java) { AppLockConfig(minutes = it) }
    }
    assertEquals(1440, AppLockConfig(minutes = 1440).minutes)
  }
}
