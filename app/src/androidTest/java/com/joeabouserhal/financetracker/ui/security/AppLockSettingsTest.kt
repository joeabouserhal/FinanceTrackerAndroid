package com.joeabouserhal.financetracker.ui.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.joeabouserhal.financetracker.data.security.*
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic settings: never enables the lock or writes to the phone's real preferences. */
class AppLockSettingsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun dedicatedPageHasHeadingAndBackWithoutBottomNavigation() {
    var back = false
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AppLockPage({ back = true }) {
        AppLockSettingsContent(AppLockConfig(), "5", {}, true, null, {}, {}, {})
      }
    } }
    compose.onNodeWithText("APP LOCK").assertIsDisplayed()
    compose.onNodeWithContentDescription("App Lock").assertIsDisplayed()
    compose.onNodeWithText("OPTIONS").assertDoesNotExist()
    compose.onNodeWithText("< BACK").performClick()
    compose.runOnIdle { assertTrue(back) }
  }

  @Test fun disabledHidesTimingAndToggleRequestsEnable() {
    var request = false
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AppLockSettingsContent(AppLockConfig(), "5", {}, true, null, { request = it }, {}, {})
    } }
    compose.onNodeWithText("LOCK WHEN").assertDoesNotExist()
    compose.onNodeWithContentDescription("App Lock").assertIsOff().performClick()
    compose.runOnIdle { assertTrue(request) }
  }

  @Test fun captureProtectionCanBeEnabledWithAppLockOff() {
    var requested = false
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AppLockPage({}) {
        AppLockSettingsContent(AppLockConfig(), "5", {}, true, null, {}, {}, {},
          onCaptureProtection = { requested = it })
      }
    } }
    compose.onNodeWithContentDescription("Block screenshots & screen mirroring")
      .performScrollTo().assertIsOff().performClick()
    compose.runOnIdle { assertTrue(requested) }
  }

  @Test fun customTimeoutValidationAndApply() {
    var config by mutableStateOf(AppLockConfig(true, AppLockTiming.AFTER_MINUTES, 5))
    var minutes by mutableStateOf("5")
    var applied = 0
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppLockSettingsContent(config, minutes, { minutes = it }, true, null, {}, { config = config.copy(timing = it) }, { applied++ })
      }
    } }
    compose.onNodeWithText("APPLY").assertIsNotEnabled()
    compose.onNodeWithText("MINUTES AWAY").performTextReplacement("0")
    compose.onNodeWithText("APPLY").assertIsNotEnabled()
    compose.onNodeWithText("Enter between 1 and 1440 minutes.").assertExists()
    compose.onNodeWithText("MINUTES AWAY").performTextReplacement("17")
    compose.onNodeWithText("APPLY").assertIsEnabled().performClick()
    compose.runOnIdle { assertEquals(1, applied) }
    compose.onNodeWithText("APP RESTARTS").performScrollTo().performClick()
    compose.onNodeWithText("MINUTES AWAY").assertDoesNotExist()
  }

  @Test fun timingChoicesAreStackedAndSelectOneOption() {
    var config by mutableStateOf(AppLockConfig(enabled = true))
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AppLockPage({}) {
        AppLockSettingsContent(config, "5", {}, true, null, {}, { config = config.copy(timing = it) }, {})
      }
    } }
    val choices = listOf("APP RESTARTS", "I LEAVE THE APP", "AFTER TIME AWAY")
    val bounds = choices.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
    bounds.zipWithNext().forEach { (above, below) ->
      assertTrue(above.bottom <= below.top)
      assertEquals(above.left, below.left)
      assertEquals(above.right, below.right)
    }
    compose.onNodeWithText("I LEAVE THE APP").assertIsSelected()
    compose.onNodeWithText("APP RESTARTS").performClick().assertIsSelected()
    compose.onNodeWithText("I LEAVE THE APP").assertIsNotSelected()
    compose.onNodeWithText("AFTER TIME AWAY").performClick().assertIsSelected()
    compose.onNodeWithText("MINUTES AWAY").performScrollTo().assertIsDisplayed()
  }

  @Test fun applyButtonMatchesOutlinedInputAtNormalText() = checkApplyAlignment(1f)
  @Test fun applyButtonMatchesOutlinedInputAtLargeText() = checkApplyAlignment(2f)

  private fun checkApplyAlignment(fontScale: Float) {
    var labelOverhang = 0.dp
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
          val scaledDensity = LocalDensity.current
          labelOverhang = with(scaledDensity) { MaterialTheme.typography.labelMedium.lineHeight.toDp() / 2 }
          AppLockPage({}) {
            AppLockSettingsContent(AppLockConfig(true, AppLockTiming.AFTER_MINUTES), "12", {}, true, null, {}, {}, {})
          }
        }
      }
    }
    compose.onNodeWithText("APPLY").performScrollTo().assertIsDisplayed()
    val field = compose.onNodeWithTag("app-lock-timeout-input").getUnclippedBoundsInRoot()
    val button = compose.onNodeWithText("APPLY").getUnclippedBoundsInRoot()
    assertTrue("Bottom borders should align", kotlin.math.abs((field.bottom - button.bottom).value) <= 1f)
    assertTrue("Top borders should align", kotlin.math.abs((field.top + labelOverhang - button.top).value) <= 1f)
  }

  @Test fun controlsRemainReachableAtLargeTextInLightTheme() {
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
        FinanceTrackerTheme(ThemeCatalog.LightBrutalist) {
          Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AppLockSettingsContent(AppLockConfig(true, AppLockTiming.AFTER_MINUTES), "12", {}, true, null, {}, {}, {})
          }
        }
      }
    }
    compose.onNodeWithText("APPLY").performScrollTo().assertIsDisplayed().assertIsEnabled()
    compose.onNodeWithContentDescription("App Lock").performScrollTo().assertIsDisplayed().assertIsOn()
  }
}
