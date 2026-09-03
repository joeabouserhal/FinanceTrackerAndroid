package com.joeabouserhal.financetracker.ui.report

import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.ui.transactions.SortOrder
import com.joeabouserhal.financetracker.ui.transactions.TransactionFilterState
import com.joeabouserhal.financetracker.ui.transactions.TransactionFiltering
import com.joeabouserhal.financetracker.ui.transactions.TypeFilter
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal enum class ReportPeriodKind { MONTH, CUSTOM, ALL }

internal data class ReportWindow(val start: LocalDate, val end: LocalDate) {
  init { require(!end.isBefore(start)) }
  val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1
  operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

internal data class ReportPeriod(
  val kind: ReportPeriodKind = ReportPeriodKind.MONTH,
  val month: YearMonth = YearMonth.now(),
  val custom: ReportWindow? = null,
) {
  fun window(today: LocalDate): ReportWindow? = when (kind) {
    ReportPeriodKind.ALL -> null
    ReportPeriodKind.CUSTOM -> requireNotNull(custom)
    ReportPeriodKind.MONTH -> ReportWindow(month.atDay(1), if (month == YearMonth.from(today)) today else month.atEndOfMonth())
  }

  fun previous(today: LocalDate): ReportWindow? = when (kind) {
    ReportPeriodKind.ALL -> null
    ReportPeriodKind.CUSTOM -> requireNotNull(custom).let { ReportWindow(it.start.minusDays(it.days), it.start.minusDays(1)) }
    ReportPeriodKind.MONTH -> month.minusMonths(1).let { prior ->
      ReportWindow(prior.atDay(1), if (month == YearMonth.from(today)) prior.atDay(minOf(today.dayOfMonth, prior.lengthOfMonth())) else prior.atEndOfMonth())
    }
  }
}

internal data class ReportBucket(val window: ReportWindow, val amountMinor: Long)
internal data class CategoryReport(
  val id: String,
  val name: String,
  val color: String,
  val amountMinor: Long,
  val share: Double,
  val transactions: List<TransactionListItem>,
)

internal data class CurrencyReport(
  val currencyId: String,
  val code: String,
  val name: String,
  val symbol: String,
  val totalMinor: Long,
  val previousTotalMinor: Long?,
  val count: Int,
  val categories: List<CategoryReport>,
  val buckets: List<ReportBucket>,
) {
  val changePercent: Double? get() = previousTotalMinor?.takeIf { it > 0 }?.let {
    (totalMinor.toDouble() - it.toDouble()) / it.toDouble() * 100.0
  }
}

internal fun reportRefinementCount(filters: TransactionFilterState): Int =
  listOf(filters.categoryIds, filters.accountIds, filters.currencyIds).count { it.isNotEmpty() }

/** Local-only, pure aggregation. Previous and current windows use identical refinements. */
internal fun buildReports(
  items: List<TransactionListItem>,
  currencies: List<CurrencyEntity>,
  viewType: TransactionType = TransactionType.EXPENSE,
  period: ReportPeriod = ReportPeriod(kind = ReportPeriodKind.ALL),
  filters: TransactionFilterState = TransactionFilterState(),
  today: LocalDate = LocalDate.now(),
): List<CurrencyReport> {
  val matching = TransactionFiltering.apply(items, filters.copy(
    type = if (viewType == TransactionType.INCOME) TypeFilter.INCOME else TypeFilter.EXPENSE,
    dateFrom = null, dateTo = null, sort = SortOrder.NEWEST,
  )).mapNotNull { item -> runCatching { LocalDate.parse(item.transaction.date) }.getOrNull()?.let { item to it } }
  val window = period.window(today)
  val previous = period.previous(today)
  val currencyById = currencies.associateBy { it.id }
  val priorTotals = matching.filter { previous != null && it.second in previous }
    .groupBy { it.first.transaction.currencyId }.mapValues { (_, group) -> group.sumOf { it.first.transaction.amount } }
  return matching.filter { window == null || it.second in window }
    .groupBy { it.first.transaction.currencyId }.entries
    .sortedByDescending { currencyById[it.key]?.isDefault == true }
    .mapNotNull { (id, dated) ->
      val currency = currencyById[id] ?: return@mapNotNull null
      val current = dated.map { it.first }
      val total = current.sumOf { it.transaction.amount }
      val categories = current.groupBy { it.transaction.categoryId }.map { (categoryId, transactions) ->
        val amount = transactions.sumOf { it.transaction.amount }
        CategoryReport(categoryId, transactions.first().categoryName, transactions.first().categoryColor,
          amount, if (total > 0) amount.toDouble() / total else 0.0, transactions)
      }.sortedWith(compareByDescending<CategoryReport> { it.amountMinor }.thenBy { it.name }.thenBy { it.id })
      val chartWindow = window ?: ReportWindow(dated.minOf { it.second }, dated.maxOf { it.second })
      val bucketDays = (chartWindow.days + 30) / 31
      val totalsByBucket = dated.groupBy { ChronoUnit.DAYS.between(chartWindow.start, it.second) / bucketDays }
        .mapValues { (_, group) -> group.sumOf { it.first.transaction.amount } }
      val buckets = (0L until (chartWindow.days + bucketDays - 1) / bucketDays).map { index ->
        val start = chartWindow.start.plusDays(index * bucketDays)
        ReportBucket(ReportWindow(start, minOf(start.plusDays(bucketDays - 1), chartWindow.end)), totalsByBucket[index] ?: 0L)
      }
      CurrencyReport(id, currency.code, currency.name, currency.symbol, total,
        if (previous != null) priorTotals[id] ?: 0L else null, current.size, categories, buckets)
    }
}
