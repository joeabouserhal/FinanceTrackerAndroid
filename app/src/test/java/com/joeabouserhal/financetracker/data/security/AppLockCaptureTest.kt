package com.joeabouserhal.financetracker.data.security

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLockCaptureTest {
  @Test fun secureFlagIsOptInAndCanBeDisabledLive() {
    Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
      val activity = controller.get()
      fun secure() = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = true))
      assertFalse(secure())
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = true, blockScreenCapture = true))
      assertTrue(secure())
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = true, blockScreenCapture = false))
      assertFalse(secure())
    }
  }

  @Test fun protectionWorksWithoutAppLockAndSurvivesDisablingLock() {
    Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
      val activity = controller.get()
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = true, blockScreenCapture = true))
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = false, blockScreenCapture = true))
      assertEquals(WindowManager.LayoutParams.FLAG_SECURE, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
      activity.applyAppLockCapturePolicy(AppLockConfig(enabled = false, blockScreenCapture = false))
      assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }
  }
}
