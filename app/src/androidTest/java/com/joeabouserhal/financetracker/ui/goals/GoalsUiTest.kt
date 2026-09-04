package com.joeabouserhal.financetracker.ui.goals

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic records only: no repositories, real transactions or goal changes. */
class GoalsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val currency = CurrencyEntity("usd", "test", "USD", "$", "US Dollar", true, "", "")
  private val accounts = listOf(
    AccountEntity("bank", "test", "usd", "Everyday banking", isDefault = true, createdAt = "", updatedAt = ""),
    AccountEntity("cash", "test", "usd", "Cash", createdAt = "", updatedAt = ""),
  )
  private val goal = GoalEntity("travel", "test", "Summer in Japan", 150000, "usd", null, false, "", "")
  private fun progress(balance: Long = 170000) = GoalProgress(goal, currency, null, balance)

  private fun launch(large: Boolean = false, light: Boolean = false, content: @Composable () -> Unit) {
    compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.5f else 1f)) {
        FinanceTrackerTheme(if (light) ThemeCatalog.CatppuccinLatte else ThemeCatalog.DarkBrutalist) {
          Box(Modifier.width(if (large) 320.dp else 400.dp).safeDrawingPadding()) { content() }
        }
      }
    }
  }

  @Test fun pageGroupsGoalsAndReviewDoesNotCompleteImmediately() {
    var reviewed = false
    val saving = progress(64000).copy(goal = goal.copy(id = "camera", name = "A better camera", targetMinor = 100000))
    launch { Box(Modifier.height(620.dp)) { GoalLibrary(listOf(saving, progress(), saving.copy(goal = saving.goal.copy(id = "home", name = "Home office"))), {}, {}, {}, { reviewed = true }) } }
    compose.onNodeWithText("READY TO COMPLETE").assertIsDisplayed()
    capture("goals-page.png")
    assertTrue(compose.onNodeWithTag("goal-travel").fetchSemanticsNode().boundsInRoot.height / compose.density.density <= 140f)
    assertTrue(compose.onNodeWithTag("goal-camera").fetchSemanticsNode().boundsInRoot.height / compose.density.density <= 120f)
    val completeFace = compose.onNodeWithTag("goal-complete-face", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(completeFace.height / compose.density.density <= 34f)
    assertTrue(completeFace.width / compose.density.density <= 100f)
    compose.onNodeWithTag("goal-home").assertIsDisplayed()
    compose.onNodeWithText("COMPLETE").performClick()
    compose.runOnIdle { assertTrue(reviewed) }
    compose.onNodeWithTag("goal-list").performScrollToNode(hasText("IN PROGRESS"))
    compose.onNodeWithText("IN PROGRESS").assertIsDisplayed()
  }

  @Test fun editorValidatesAndSavesScopeWithoutTouchingRecords() {
    var saved: List<Any?>? = null
    launch { GoalEditorDialog(null, listOf(currency), accounts, false, null, {},
      { name, target, code, account -> saved = listOf(name, target, code, account) }) }
    compose.onNodeWithText("SAVE GOAL").performClick()
    compose.onNodeWithText("Give your goal a name.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("GOAL NAME").performScrollTo().performTextInput("New bicycle")
    compose.onNodeWithText("TARGET AMOUNT").performTextInput("750.25")
    compose.onNodeWithText("EVERYDAY BANKING").performScrollTo().performClick()
    compose.onNodeWithText("SAVE GOAL").performClick()
    compose.runOnIdle { assertEquals(listOf("New bicycle", 75025L, "usd", "bank"), saved) }
  }

  @Test fun splitKeepsDraftAfterIncomingBalanceAndSaveFailure() {
    var balance by mutableStateOf(100000L)
    var error by mutableStateOf<String?>(null)
    var submitted: Map<String, Long>? = null
    launch { GoalCompletionDialog(progress(), accounts, mapOf("bank" to balance, "cash" to 70000L), false, error, {}) {
      submitted = it; error = "Couldn’t save. Please try again."
    } }
    compose.onNodeWithTag("allocation-bank").performScrollTo().performTextReplacement("900")
    compose.onNodeWithTag("allocation-cash").performScrollTo().performTextReplacement("600")
    compose.runOnIdle { balance = 95000 }
    compose.onNodeWithText("COMPLETE", substring = false).performClick()
    compose.runOnIdle { assertEquals(mapOf("bank" to 90000L, "cash" to 60000L), submitted) }
    compose.onNodeWithText("Couldn’t save. Please try again.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag("allocation-bank").performScrollTo().assert(hasText("900"))
    compose.onNodeWithText("COMPLETE GOAL").assertIsDisplayed()
    compose.onNodeWithText("CANCEL").assertIsDisplayed()
    capture("goals-complete.png")
  }

  @Test fun completedCardUsesAchievedTargetAndUndoHasConfirmation() {
    var show by mutableStateOf(false)
    var undone = false
    launch {
      GoalCard(progress(-2000).copy(goal = goal.copy(completed = true)), onUndo = { show = true })
      if (show) GoalUndoDialog(goal.name, false, null, { show = false }, { undone = true })
    }
    compose.onNodeWithText("achieved", substring = true).assertIsDisplayed()
    compose.onNodeWithText("CURRENT BALANCE").assertDoesNotExist()
    compose.onNodeWithText("UNDO").performClick()
    compose.runOnIdle { assertFalse(undone) }
    compose.onNode(hasText("UNDO") and hasAnyAncestor(isDialog())).performClick()
    compose.runOnIdle { assertTrue(undone) }
  }

  @Test fun largeTextLightEditorScrollsWithFooterVisible() {
    launch(large = true, light = true) {
      GoalEditorDialog(goal.copy(name = "A long-term goal for a much better home office"), listOf(currency), accounts,
        false, null, {}, { _, _, _, _ -> }, {})
    }
    compose.onNodeWithText("DELETE GOAL").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("SAVE GOAL").assertIsDisplayed()
    capture("goals-editor-large.png")
  }

  @Test fun lostBalanceDisablesCompletionAndLongAccountListScrolls() {
    var current by mutableStateOf(progress())
    val many = accounts + (1..12).map { accounts[1].copy(id = "cash-$it", name = "Savings account $it") }
    launch(large = true) { GoalCompletionDialog(current, many, mapOf("bank" to 170000), false, null, {}, {}) }
    compose.onNodeWithTag("allocation-cash-12").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("COMPLETE", substring = false).assertIsDisplayed().assertIsEnabled()
    compose.runOnIdle { current = progress(1000) }
    compose.onNodeWithText("COMPLETE", substring = false).assertIsNotEnabled()
  }

  @Test fun splitDraftSurvivesSavedStateRestoration() {
    val restoration = StateRestorationTester(compose)
    compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    restoration.setContent {
      FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
        GoalCompletionDialog(progress(), accounts, mapOf("bank" to 100000, "cash" to 70000), false, null, {}, {})
      }
    }
    compose.onNodeWithTag("allocation-bank").performScrollTo().performTextReplacement("820")
    restoration.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag("allocation-bank").performScrollTo().assert(hasText("820"))
    compose.onNodeWithText("COMPLETE", substring = false).assertIsNotEnabled()
  }

  private fun capture(name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureGoals") != "true") return
    compose.waitForIdle()
    SystemClock.sleep(350)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
      File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }
}
