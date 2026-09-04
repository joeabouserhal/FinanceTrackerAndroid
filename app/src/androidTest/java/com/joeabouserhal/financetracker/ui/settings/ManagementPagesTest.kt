package com.joeabouserhal.financetracker.ui.settings

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.theme.*
import com.joeabouserhal.financetracker.ui.categories.CategoryLibrary
import com.joeabouserhal.financetracker.ui.presets.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** All records are synthetic; these tests never call a repository or change the user's records. */
class ManagementPagesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val currencies = listOf(
    CurrencyEntity("eur", "guest", "EUR", "€", "Euro", false, "", ""),
    CurrencyEntity("usd", "guest", "USD", "$", "US Dollar", true, "", ""),
  )
  private val accounts = listOf(
    AccountEntity("bank", "guest", "usd", "Everyday banking", isDefault = true, createdAt = "", updatedAt = ""),
    AccountEntity("cash", "guest", "usd", "Cash", createdAt = "", updatedAt = ""),
    AccountEntity("euro", "guest", "eur", "Travel wallet", isDefault = true, createdAt = "", updatedAt = ""),
  )
  private val categories = listOf(
    CategoryEntity("food", "guest", "Food & groceries", TransactionType.EXPENSE, "#F28C28", createdAt = "", updatedAt = ""),
    CategoryEntity("travel-out", "guest", "Travel", TransactionType.EXPENSE, "#38B6C8", createdAt = "", updatedAt = ""),
    CategoryEntity("travel-in", "guest", "Travel", TransactionType.INCOME, "#4C9A63", createdAt = "", updatedAt = ""),
    CategoryEntity("other", "guest", "Other", TransactionType.EXPENSE, "#77746C", isDefault = true, createdAt = "", updatedAt = ""),
  )
  private val presets = listOf(
    PresetEntity("coffee", "guest", "Morning coffee", TransactionType.EXPENSE, 450, "usd", "food", "cash", createdAt = "", updatedAt = ""),
    PresetEntity("salary", "guest", "Monthly salary", TransactionType.INCOME, null, "usd", null, "bank", createdAt = "", updatedAt = ""),
  )

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

  private fun scroll(tag: String, text: String) = compose.onNodeWithTag(tag).performScrollToNode(hasText(text))

  @Test fun categoriesSearchAndTypeKeepDuplicateNamesSeparate() {
    var selected = ""
    launch {
      var query by remember { mutableStateOf("") }
      var filter by remember { mutableStateOf("ALL") }
      CategoryLibrary(categories, query, { query = it }, filter, { filter = it }, {}, {}, { selected = it.id })
    }
    capture("management-categories.png")
    compose.onNodeWithText("SEARCH CATEGORIES").performTextInput("Travel")
    compose.onNodeWithText("INCOME").performClick()
    scroll("category-list", "Travel")
    compose.onNode(hasText("Travel") and !hasSetTextAction()).performClick()
    compose.runOnIdle { assertEquals("travel-in", selected) }
    compose.onNodeWithText("Other").assertDoesNotExist()
  }

  @Test fun presetsSearchModesAndSelection() {
    var selected = ""
    launch {
      var filter by remember { mutableStateOf(PresetFilter.ALL) }
      PresetLibrary("guest", presets, currencies, accounts, categories, filter, { filter = it }, {}, { selected = it.id }, onAdd = {})
    }
    capture("management-presets.png")
    compose.onNodeWithText("SEARCH PRESETS").performTextInput("coffee")
    compose.onNodeWithText("INCOME").performClick()
    scroll("preset-list", "No matching presets")
    compose.onNodeWithText("No matching presets").assertIsDisplayed()
    scroll("preset-list", "ALL")
    compose.onNodeWithText("ALL").performClick()
    scroll("preset-list", "Morning coffee")
    compose.onNodeWithText("Morning coffee").performClick()
    compose.runOnIdle { assertEquals("coffee", selected) }
  }

  @Test fun currencyDefaultOrderingActionsAndArchivedRestore() {
    var action = ""
    val archived = listOf(accounts[1].copy(id = "old", name = "Old cash", archived = true))
    launch {
      CurrencyAccountLibrary("guest", currencies, accounts, archived, mapOf("bank" to 120000L, "cash" to 43000L), {}, {}, {}, {},
        { currency, selected -> action = "${currency.id}:$selected" }, { account, selected -> action = "${account.id}:$selected" })
    }
    scroll("currency-account-list", "DEFAULT CURRENCY")
    compose.onNodeWithContentDescription("Manage USD").assertIsDisplayed()
    capture("management-accounts.png")
    compose.onNodeWithContentDescription("Manage USD").performClick()
    compose.onNodeWithText("Make default").assertDoesNotExist()
    compose.onNodeWithText("CLOSE").performClick()
    compose.onNodeWithTag("currency-account-list").performScrollToNode(hasTestTag("account-footer:usd"))
    compose.onAllNodesWithTag("management-row-divider").assertCountEquals(1)
    compose.onNodeWithTag("currency-account-list").performScrollToNode(hasTestTag("currency-divider:eur"))
    compose.onNodeWithTag("currency-divider:eur").assertIsDisplayed()
    compose.onNodeWithTag("currency-divider:usd").assertDoesNotExist()
    compose.onNodeWithTag("currency-account-list").performScrollToNode(hasContentDescription("Manage EUR"))
    compose.onNodeWithContentDescription("Manage EUR").performClick()
    compose.onNodeWithText("Make default").performClick()
    compose.runOnIdle { assertEquals("eur:DEFAULT", action) }
    scroll("currency-account-list", "ARCHIVED ACCOUNTS (1)")
    compose.onNodeWithText("ARCHIVED ACCOUNTS (1)").performClick()
    scroll("currency-account-list", "Old cash")
    compose.onNodeWithText("Old cash").performClick()
    compose.onNodeWithText("Restore account").performClick()
    compose.runOnIdle { assertEquals("old:RESTORE", action) }
  }

  @Test fun largeTextLightThemeAndLongAccountNamesRemainUsable() {
    var opened = false
    launch(large = true, light = true) {
      CurrencyAccountLibrary("guest", currencies, listOf(accounts[0].copy(name = "A long everyday bank account name")), emptyList(), mapOf("bank" to 98765432100L), {}, {}, {}, { opened = true }, { _, _ -> }, { _, _ -> })
    }
    compose.onNodeWithTag("currency-account-list").performScrollToNode(hasContentDescription("Manage A long everyday bank account name"))
    compose.onNodeWithText("A long everyday bank account name").performClick()
    compose.runOnIdle { assertTrue(opened) }
    capture("management-large-text.png")
    scroll("currency-account-list", "ADD USD ACCOUNT")
    compose.onNodeWithText("ADD USD ACCOUNT").assertIsDisplayed()
  }

  @Test fun currencyFormValidationAndPinnedActionsAtLargeText() {
    var saved = false
    launch(large = true, light = true) {
      CurrencyDialog("Add currency", null, {}, { _, _, _, _ -> saved = true })
    }
    compose.onNodeWithText("SAVE").assertIsDisplayed().performClick()
    compose.runOnIdle { assertFalse(saved) }
    compose.onNodeWithText("Currency code is required").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("CODE · e.g. USD").performScrollTo().performTextInput("GBP")
    compose.onNodeWithText("NAME").performScrollTo().performTextInput("Pound sterling")
    compose.onNodeWithText("SAVE").assertIsDisplayed().performClick()
    compose.runOnIdle { assertTrue(saved) }
  }

  @Test fun accountOverviewEmptyStateScrollsWithLargeText() {
    launch(large = true) {
      AccountDetailContent(AccountDetailState("Long everyday bank account", "USD", "$", 98765432100L, 120000, 21000, emptyList()), {}, {})
    }
    scroll("account-detail-list", "No activity yet")
    compose.onNodeWithText("No activity yet").assertIsDisplayed()
  }

  private fun capture(name: String) {
    if (InstrumentationRegistry.getArguments().getString("captureManagement") != "true") return
    compose.waitForIdle()
    SystemClock.sleep(400)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
      File(instrumentation.targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }
}
