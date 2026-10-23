package com.joeabouserhal.financetracker.ui.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.repositories.AccountBalance
import com.joeabouserhal.financetracker.data.repositories.CurrencyBalance
import com.joeabouserhal.financetracker.data.repositories.MonthlyActivity
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.ui.components.LocalAmountsHidden
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class DashboardPrivacyTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val currency = CurrencyEntity("usd", "test", "USD", "$", "US Dollar", createdAt = "now", updatedAt = "now")
  private fun account(id: String, name: String, amount: Long) = AccountBalance(
    AccountEntity(id, "test", "usd", name, createdAt = "now", updatedAt = "now"), currency, amount)
  private val balance = CurrencyBalance(currency, 100000,
    listOf(account("bank", "Bank", 70000), account("cash", "Cash", 30000)), hasActivity = true)

  @Test fun hiddenBalancesConcealDigitsAndSymbolsButNotAccountNames() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      CompositionLocalProvider(LocalAmountsHidden provides true) { CurrencyBalanceBlock(balance) }
    } }
    listOf("1,000", "$700", "$300", "$").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    compose.onNodeWithText("Bank").assertIsDisplayed()
    compose.onNodeWithText("Cash").assertIsDisplayed()
    compose.onNodeWithText("USD · US Dollar").assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Amount hidden").assertCountEquals(4)
  }

  @Test fun activityMonthStaysVisibleWhileBothIncomeAndExpenseAreHidden() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) {
      CompositionLocalProvider(LocalAmountsHidden provides true) {
        ActivitySection(YearMonth.of(2026, 10), {}, {}, {}, listOf(MonthlyActivity(currency, 20000, 10000)))
      }
    } }
    compose.onNodeWithText("OCTOBER 2026").assertIsDisplayed()
    compose.onNodeWithText("USD").assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Amount hidden").assertCountEquals(2)
    compose.onNodeWithText("-\u2009$100", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithText("+\u2009$200", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test fun turningPrivacyOffShowsFullBalancesAgain() {
    compose.setContent { FinanceTrackerTheme(ThemeCatalog.LightBrutalist) { CurrencyBalanceBlock(balance) } }
    listOf("1,000", "$700", "$300", "Bank", "Cash").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    compose.onAllNodesWithContentDescription("Amount hidden").assertCountEquals(0)
  }
}
