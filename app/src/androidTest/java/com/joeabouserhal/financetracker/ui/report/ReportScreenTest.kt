package com.joeabouserhal.financetracker.ui.report

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.io.File
import android.graphics.Bitmap

class ReportScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val today = LocalDate.parse("2024-03-20")
  private val owner = mutableStateOf("guest")
  private val shown = mutableStateOf(true)
  private val data = mutableStateOf(fixtures())
  private val currencies = listOf(CurrencyEntity("USD", "guest", "USD", "$", "US Dollar", true, "", ""),
    CurrencyEntity("EUR", "guest", "EUR", "€", "Euro", false, "", ""))

  private fun launch(light: Boolean = false, largeText: Boolean = false) {
    compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 1.5f else 1f)) {
        FinanceTrackerTheme(if (light) ThemeCatalog.CatppuccinLatte else ThemeCatalog.Dracula) {
          val holder = rememberSaveableStateHolder()
          if (shown.value) holder.SaveableStateProvider("report") {
            Box(Modifier.width(if (largeText) 320.dp else 400.dp)) {
              ReportContent(owner.value, data.value, currencies, today, refinementPanel = { _, _, _ -> Text("Refinement controls") })
            }
          }
        }
      }
    }
  }

  private fun scroll(text: String) = compose.onNodeWithTag("report-list").performScrollToNode(hasText(text))

  @Test fun monthNavigationModesChartAndCategoryExpansion() {
    launch()
    compose.onNodeWithText("This month").assertIsDisplayed()
    compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Previous month").performClick()
    compose.onNodeWithText("Feb 2024").assertIsDisplayed()
    compose.onNodeWithContentDescription("Next month").performClick()
    compose.onNodeWithText("EARNING").performClick()
    scroll("TOTAL EARNED")
    compose.onNodeWithText("TOTAL EARNED").assertIsDisplayed()
    scroll("SPENDING")
    compose.onNodeWithText("SPENDING").performClick()
    compose.onNodeWithTag("report-list").performScrollToNode(hasTestTag("trend:USD"))
    compose.onNodeWithTag("bucket:USD:0").performClick()
    compose.onNodeWithText("1 Mar 2024 · $0").assertExists()
    scroll("Show all categories (7)")
    compose.onNodeWithText("Show all categories (7)").performClick()
    scroll("Category 1")
    compose.onNodeWithText("Category 1").assertIsDisplayed()
    scroll("Show less")
    compose.onNodeWithText("Show less").performClick()
    compose.runOnIdle { shown.value = false }
    compose.runOnIdle { shown.value = true }
    scroll("Show all categories (7)")
    compose.onNodeWithText("Show all categories (7)").assertIsDisplayed()
  }

  @Test fun lightThemeLargeTextModalStaysScrollableAndReflectsDeletedData() {
    launch(light = true, largeText = true)
    scroll("Category 7")
    compose.onNodeWithText("Category 7").performClick()
    compose.onNodeWithText("CATEGORY 7").assertIsDisplayed()
    compose.onNodeWithText("CLOSE").assertIsDisplayed()
    compose.onNodeWithTag("category-transactions").performScrollToIndex(12)
    compose.onNodeWithText("CLOSE").assertIsDisplayed()
    capture("report-modal-qa.png")
    compose.runOnIdle { data.value = data.value.filterNot { it.transaction.categoryId == "cat7" } }
    compose.onNodeWithText("No matching transactions remain in this report.").assertIsDisplayed()
    compose.onNodeWithText("CLOSE").performClick()
    scroll("This month")
    compose.onNodeWithText("This month").performClick()
    compose.onNodeWithText("CUSTOM").performClick()
    compose.onNodeWithText("APPLY").assertIsDisplayed()
    compose.onNodeWithText("CANCEL").performClick()
    capture("report-light-qa.png")
  }

  @Test fun periodPickerAllTimeAndPartitionReset() {
    launch()
    capture("report-dark-qa.png")
    compose.onNodeWithText("This month").performClick()
    compose.onNodeWithContentDescription("Previous year").performClick()
    compose.onNodeWithText("2023").assertIsDisplayed()
    compose.onNodeWithText("ALL TIME").performClick()
    compose.onNodeWithText("APPLY").performClick()
    compose.onNodeWithText("All time").assertIsDisplayed()
    compose.onNodeWithText("FILTER").performClick()
    scroll("Refinement controls")
    compose.onNodeWithText("Refinement controls").assertIsDisplayed()
    compose.runOnIdle { shown.value = false }
    compose.runOnIdle { shown.value = true }
    scroll("All time")
    compose.onNodeWithText("All time").assertIsDisplayed()
    compose.runOnIdle { owner.value = "another-owner" }
    compose.onNodeWithText("This month").assertIsDisplayed()
    compose.onNodeWithText("Refinement controls").assertDoesNotExist()
  }

  private fun capture(name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureReport") != "true") return
    compose.onNodeWithTag("report-list").assertExists()
    compose.waitForIdle()
    android.os.SystemClock.sleep(400) // Let the platform dialog-window animation settle.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
  }

  private fun fixtures(): List<TransactionListItem> {
    fun tx(id: String, category: Int, amount: Long, type: TransactionType = TransactionType.EXPENSE, currency: String = "USD") = TransactionListItem(
      TransactionEntity(id, "guest", type, amount, currency, "cat$category", null, "2024-03-10", "Transaction $id", null, null, null, "2024-03-10T12:00:00Z", "2024-03-10T12:00:00Z"),
      "Category $category", "#CBA6F7", if (currency == "USD") "$" else "€", "Cash",
    )
    return (1..7).map { tx("$it", it, it * 100L) } + (1..15).map { tx("extra$it", 7, 100L) } +
      tx("income", 8, 100000L, TransactionType.INCOME) + tx("euro", 1, 900L, currency = "EUR")
  }
}
