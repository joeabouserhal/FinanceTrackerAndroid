package com.joeabouserhal.financetracker.ui.report

import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReportGroupingTest {
  private val timestamp = "2026-09-03T00:00:00Z"
  private val currencies = listOf(currency("EUR"), currency("USD", true), currency("GBP"))
  private val items = listOf(item("EUR", 100), item("GBP", 200), item("USD", 300))

  @Test
  fun `default comes first while other groups and totals stay unchanged`() {
    val reports = buildReports(items, currencies)
    assertEquals(listOf("USD", "EUR", "GBP"), reports.map { it.code })
    assertEquals(listOf(300L, 100L, 200L), reports.map { it.totalMinor })
    assertEquals(listOf(1, 1, 1), reports.map { it.count })
  }

  @Test
  fun `changing default changes first group without changing transaction order`() {
    val updated = currencies.map { it.copy(isDefault = it.code == "GBP") }
    assertEquals(listOf("GBP", "EUR", "USD"), buildReports(items, updated).map { it.code })
  }

  @Test
  fun `filtered default and missing currencies do not create empty groups`() {
    assertEquals(listOf("EUR", "GBP"), buildReports(items.take(2), currencies).map { it.code })
    assertEquals(listOf("EUR"), buildReports(items, currencies.take(1)).map { it.code })
    assertEquals(emptyList<CurrencyReport>(), buildReports(emptyList(), currencies))
  }

  private fun currency(code: String, isDefault: Boolean = false) = CurrencyEntity(
    id = code, ownerId = "guest", code = code, symbol = code, name = code,
    isDefault = isDefault, createdAt = timestamp, updatedAt = timestamp,
  )

  private fun item(currency: String, amount: Long) = TransactionListItem(
    transaction = TransactionEntity(
      id = currency, ownerId = "guest", type = TransactionType.EXPENSE, amount = amount,
      currencyId = currency, categoryId = "category", accountId = null, date = "2026-09-03",
      title = null, notes = null, presetId = null, goalId = null,
      createdAt = timestamp, updatedAt = timestamp,
    ),
    categoryName = "Other", categoryColor = "#777777", currencySymbol = currency, accountName = null,
  )
}
