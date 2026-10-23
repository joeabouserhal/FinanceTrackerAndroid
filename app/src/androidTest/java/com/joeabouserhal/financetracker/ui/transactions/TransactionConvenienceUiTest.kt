package com.joeabouserhal.financetracker.ui.transactions

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.ui.components.LocalAmountsHidden
import com.joeabouserhal.financetracker.ui.components.PrivateAmount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionConvenienceUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun hiddenAmountsDoNotExposeTheirTextToAccessibility() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      CompositionLocalProvider(LocalAmountsHidden provides true) { PrivateAmount("$1,234.56") }
    } }
    compose.onNodeWithText("$1,234.56", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithContentDescription("Amount hidden").assertIsDisplayed()
  }

  @Test fun visibleAmountsRemainReadable() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.LightBrutalist) { PrivateAmount("$1,234.56") } }
    compose.onNodeWithText("$1,234.56").assertIsDisplayed()
  }

  @Test fun rightSwipeOnlyRevealsCopyUntilExplicitlyPressed() {
    var copies = 0; var deletes = 0
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      SwipeRevealRow(onDelete = { deletes++ }, onDuplicate = { copies++ }, modifier = Modifier.testTag("row")) {
        Box(Modifier.fillMaxWidth().height(72.dp)) { Text("An entry") }
      }
    } }
    compose.onNodeWithTag("row").performTouchInput { swipeRight() }
    compose.waitForIdle()
    compose.onNodeWithText("COPY").assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, copies); assertEquals(0, deletes) }
    // The whole revealed button is tappable, not only its text label.
    compose.onNodeWithText("COPY").performTouchInput { click(androidx.compose.ui.geometry.Offset(4f, 4f)) }
    compose.runOnIdle { assertEquals(1, copies); assertEquals(0, deletes) }
  }

  @Test fun swipingCopyClosedDoesNotCreateAnything() {
    var copies = 0; var deletes = 0
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      SwipeRevealRow(onDelete = { deletes++ }, onDuplicate = { copies++ }, modifier = Modifier.testTag("row")) {
        Box(Modifier.fillMaxWidth().height(72.dp)) { Text("An entry") }
      }
    } }
    compose.onNodeWithTag("row").performTouchInput { swipeRight() }
    compose.waitForIdle()
    compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(0, copies); assertEquals(0, deletes) }
  }

  @Test fun leftSwipeOnlyRevealsDeleteUntilExplicitlyPressed() {
    var copies = 0; var deletes = 0
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      SwipeRevealRow(onDelete = { deletes++ }, onDuplicate = { copies++ }, modifier = Modifier.testTag("row")) {
        Box(Modifier.fillMaxWidth().height(72.dp)) { Text("An entry") }
      }
    } }
    compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(0, deletes); assertEquals(0, copies) }
    compose.onNodeWithText("DELETE").performClick()
    compose.runOnIdle { assertEquals(1, deletes); assertEquals(0, copies) }
  }
}
