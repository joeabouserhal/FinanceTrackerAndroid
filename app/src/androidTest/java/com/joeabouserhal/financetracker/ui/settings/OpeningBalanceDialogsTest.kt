package com.joeabouserhal.financetracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic forms only: callbacks capture values without saving any phone records. */
class OpeningBalanceDialogsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val usd = CurrencyEntity("usd", "test", "USD", "$", "Dollar", createdAt = "", updatedAt = "")

  @Test fun currencyNameIsAboveSideBySideSymbolAndCodeAndSavesStartingFunds() {
    var saved: Long? = null
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      CurrencyDialog("Add currency", null, {}, { code, symbol, name, _, opening ->
        assertEquals("USD", code); assertEquals("$", symbol); assertEquals("Dollar", name); saved = opening
      })
    } }
    val name = compose.onNodeWithText("NAME · United States Dollar").getUnclippedBoundsInRoot()
    val symbol = compose.onNodeWithText("SYMBOL · $").getUnclippedBoundsInRoot()
    val code = compose.onNodeWithText("CODE · USD").getUnclippedBoundsInRoot()
    assertTrue(name.bottom < symbol.top)
    assertEquals(symbol.top, code.top)
    assertTrue(symbol.right < code.left)
    compose.onNodeWithText("NAME · United States Dollar").performTextInput("Dollar")
    compose.onNodeWithText("SYMBOL · $").performTextInput("$")
    compose.onNodeWithText("CODE · USD").performTextInput("USD")
    compose.onNodeWithText("OPENING BALANCE (OPTIONAL)").performScrollTo().performTextInput("1234.56")
    compose.onNodeWithText("SAVE").performClick()
    compose.runOnIdle { assertEquals(123456L, saved) }
  }

  @Test fun newAccountOpeningBalanceIsOptionalAndDefaultsToZero() {
    var saved: Long? = null
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AccountDialog("Add account", "", "usd", listOf(usd), {}, { _, currency, opening ->
        assertEquals("usd", currency); saved = opening
      }, allowOpeningBalance = true)
    } }
    compose.onNodeWithText("ACCOUNT NAME").performTextInput("Bank")
    compose.onNodeWithText("OPENING BALANCE (OPTIONAL)").assertIsDisplayed()
    compose.onNodeWithText("SAVE").performClick()
    compose.runOnIdle { assertEquals(0L, saved) }
  }

  @Test fun overdraftStartingBalanceIsAcceptedWithoutBecomingAnExpense() {
    var saved: Long? = null
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.LightBrutalist) {
      AccountDialog("Add account", "Bank", "usd", listOf(usd), {}, { _, _, opening -> saved = opening }, allowOpeningBalance = true)
    } }
    compose.onNodeWithText("OPENING BALANCE (OPTIONAL)").performTextInput("-50.25")
    compose.onNodeWithText("SAVE").performClick()
    compose.runOnIdle { assertEquals(-5025L, saved) }
  }

  @Test fun malformedStartingAmountBlocksSaving() {
    var saved = false
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AccountDialog("Add account", "Bank", "usd", listOf(usd), {}, { _, _, _ -> saved = true }, allowOpeningBalance = true)
    } }
    compose.onNodeWithText("OPENING BALANCE (OPTIONAL)").performTextInput("1+2")
    compose.onNodeWithText("SAVE").performClick()
    compose.onNodeWithText("Enter a valid opening balance").performScrollTo().assertIsDisplayed()
    compose.runOnIdle { assertFalse(saved) }
  }

  @Test fun editingDoesNotOfferToAddStartingFundsAgain() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AccountDialog("Edit account", "Cash", "usd", listOf(usd), {}, { _, _, _ -> })
    } }
    compose.onNodeWithText("OPENING BALANCE (OPTIONAL)").assertDoesNotExist()
  }

  @Test fun saveIsDisabledWhileCreatingTheAccount() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      AccountDialog("Add account", "Bank", "usd", listOf(usd), {}, { _, _, _ -> }, allowOpeningBalance = true, busy = true)
    } }
    compose.onNodeWithText("SAVE").assertIsNotEnabled()
  }
}
