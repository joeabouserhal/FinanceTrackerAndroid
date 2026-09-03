package com.joeabouserhal.financetracker.ui.report

import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.ui.transactions.TransactionFilterState
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportModelTest {
  private val today = LocalDate.parse("2024-03-31")
  private val month = ReportPeriod(month = YearMonth.from(today))
  private val currency = CurrencyEntity("USD", "guest", "USD", "$", "Dollar", true, "", "")

  private fun tx(id: String, date: String, amount: Long = 100, category: String = "food", account: String = "cash", type: TransactionType = TransactionType.EXPENSE) = TransactionListItem(
    TransactionEntity(id, "guest", type, amount, "USD", category, account, date, id, null, null, null, "${date}T12:00:00Z", "${date}T12:00:00Z"),
    "Same category name", "#777777", "$", account,
  )

  private fun report(items: List<TransactionListItem>, period: ReportPeriod = month, filters: TransactionFilterState = TransactionFilterState(), view: TransactionType = TransactionType.EXPENSE) =
    buildReports(items, listOf(currency), view, period, filters, today)

  @Test fun `current month comparison clamps to leap day`() {
    assertEquals(ReportWindow(LocalDate.parse("2024-03-01"), today), month.window(today))
    assertEquals(ReportWindow(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-29")), month.previous(today))
  }

  @Test fun `partial month uses matching elapsed days and excludes future activity`() {
    val now = LocalDate.parse("2024-03-10")
    assertEquals(LocalDate.parse("2024-02-10"), month.previous(now)!!.end)
    val reports = buildReports(listOf(tx("past", "2024-03-01"), tx("future", "2024-03-11")), listOf(currency), period = month, today = now)
    assertEquals(100L, reports.single().totalMinor)
    assertEquals(10, reports.single().buckets.size)
  }

  @Test fun `historical and year boundary months compare complete months`() {
    val january = ReportPeriod(month = YearMonth.of(2024, 1))
    assertEquals(ReportWindow(LocalDate.parse("2023-12-01"), LocalDate.parse("2023-12-31")), january.previous(today))
    assertEquals(LocalDate.parse("2024-01-31"), january.window(today)!!.end)
    val nonLeap = ReportPeriod(month = YearMonth.of(2025, 3))
    assertEquals(LocalDate.parse("2025-02-28"), nonLeap.previous(LocalDate.parse("2025-03-31"))!!.end)
  }

  @Test fun `custom range comparison is adjacent and equal in length`() {
    val custom = ReportPeriod(ReportPeriodKind.CUSTOM, custom = ReportWindow(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-03-03")))
    assertEquals(ReportWindow(LocalDate.parse("2024-02-27"), LocalDate.parse("2024-02-29")), custom.previous(today))
    val result = report(listOf(tx("now", "2024-03-03", 200), tx("before", "2024-02-29", 100)), custom).single()
    assertEquals(100.0, result.changePercent!!, 0.0001)
  }

  @Test fun `account and category filters apply equally to previous and current`() {
    val result = report(listOf(tx("current", "2024-03-10", 200), tx("prior", "2024-02-10", 100),
      tx("otherAccount", "2024-02-10", 999, account = "bank"), tx("otherCategory", "2024-03-10", 999, category = "rent")),
      filters = TransactionFilterState(accountIds = setOf("cash"), categoryIds = setOf("food"))).single()
    assertEquals(200L, result.totalMinor)
    assertEquals(100L, result.previousTotalMinor)
    assertEquals(100.0, result.changePercent!!, 0.001)
  }

  @Test fun `no previous activity never produces infinite percentage`() {
    val result = report(listOf(tx("only", "2024-03-02"))).single()
    assertEquals(0L, result.previousTotalMinor)
    assertNull(result.changePercent)
    val all = report(listOf(tx("only", "2024-03-02")), ReportPeriod(ReportPeriodKind.ALL)).single()
    assertNull(all.previousTotalMinor)
    assertNull(all.changePercent)
  }

  @Test fun `daily buckets include zero days and sum exactly to total`() {
    val result = report(listOf(tx("one", "2024-03-01"), tx("two", "2024-03-31", 200))).single()
    assertEquals(31, result.buckets.size)
    assertEquals(29, result.buckets.count { it.amountMinor == 0L })
    assertEquals(result.totalMinor, result.buckets.sumOf { it.amountMinor })
  }

  @Test fun `long ranges have contiguous bounded buckets with no missing money`() {
    val period = ReportPeriod(ReportPeriodKind.CUSTOM, custom = ReportWindow(LocalDate.parse("2020-01-01"), today))
    val result = report(listOf(tx("first", "2020-01-01", 123), tx("middle", "2022-04-10", 456), tx("last", "2024-03-31", 789)), period).single()
    assertTrue(result.buckets.size <= 31)
    assertEquals(period.custom!!.start, result.buckets.first().window.start)
    assertEquals(today, result.buckets.last().window.end)
    result.buckets.zipWithNext().forEach { (left, right) -> assertEquals(left.window.end.plusDays(1), right.window.start) }
    assertEquals(1368L, result.buckets.sumOf { it.amountMinor })
  }

  @Test fun `category identity not label determines grouping and shares include every category`() {
    val result = report((1..7).map { tx("$it", "2024-03-10", it * 100L, category = "category$it") }).single()
    assertEquals(7, result.categories.size)
    assertEquals("category7", result.categories.first().id)
    assertEquals(1.0, result.categories.sumOf { it.share }, 0.000001)
    assertEquals(700.0 / 2800, result.categories.first().share, 0.000001)
  }

  @Test fun `goal withdrawals count as spending and detail transactions stay newest first`() {
    val items = listOf(tx("old", "2024-03-01"), tx("goal", "2024-03-10", 200, type = TransactionType.GOAL), tx("income", "2024-03-15", 900, type = TransactionType.INCOME))
    val expense = report(items).single()
    assertEquals(300L, expense.totalMinor)
    assertEquals(listOf("goal", "old"), expense.categories.single().transactions.map { it.transaction.id })
    assertEquals(900L, report(items, view = TransactionType.INCOME).single().totalMinor)
  }

  @Test fun `large amounts retain integer totals and finite shares`() {
    val result = report(listOf(tx("one", "2024-03-01", 900000000000000L), tx("two", "2024-03-02", 800000000000000L))).single()
    assertEquals(1700000000000000L, result.totalMinor)
    assertEquals(1.0, result.categories.single().share, 0.0)
  }

  @Test fun `empty mode or period yields no currency groups and refinements exclude dates`() {
    assertTrue(report(listOf(tx("before", "2024-02-01"))).isEmpty())
    assertTrue(report(listOf(tx("expense", "2024-03-01")), view = TransactionType.INCOME).isEmpty())
    assertEquals(0, reportRefinementCount(TransactionFilterState(dateFrom = "2024-03-01")))
    assertEquals(3, reportRefinementCount(TransactionFilterState(categoryIds = setOf("a"), accountIds = setOf("b"), currencyIds = setOf("c"))))
  }
}
