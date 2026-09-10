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

internal enum class ReportPeriodKind { MONTH, YEAR, CUSTOM, ALL }

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
    ReportPeriodKind.YEAR -> ReportWindow(
      LocalDate.of(month.year, 1, 1),
      if (month.year == today.year) today else LocalDate.of(month.year, 12, 31),
    )
  }

  fun previous(today: LocalDate): ReportWindow? = when (kind) {
    ReportPeriodKind.ALL -> null
    ReportPeriodKind.CUSTOM -> requireNotNull(custom).let { ReportWindow(it.start.minusDays(it.days), it.start.minusDays(1)) }
    ReportPeriodKind.MONTH -> month.minusMonths(1).let { prior ->
      ReportWindow(prior.atDay(1), if (month == YearMonth.from(today)) prior.atDay(minOf(today.dayOfMonth, prior.lengthOfMonth())) else prior.atEndOfMonth())
    }
    ReportPeriodKind.YEAR -> ReportWindow(LocalDate.of(month.year - 1, 1, 1), LocalDate.of(month.year - 1, 12, 31))
  }
}

internal data class ReportBucket(
  val window: ReportWindow,
  val amountMinor: Long,
  val count: Int,
  val categories: List<CategoryReport>,
)
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
      val categories = categoryReports(current, total)
      val chartWindow = window ?: ReportWindow(dated.minOf { it.second }, dated.maxOf { it.second })
      val buckets = buildBuckets(dated, chartWindow, period.kind)
      CurrencyReport(id, currency.code, currency.name, currency.symbol, total,
        if (previous != null) priorTotals[id] ?: 0L else null, current.size, categories, buckets)
    }
}

private fun categoryReports(transactions: List<TransactionListItem>, total: Long): List<CategoryReport> =
  transactions.groupBy { it.transaction.categoryId }.map { (categoryId, group) ->
    val amount = group.sumOf { it.transaction.amount }
    CategoryReport(
      categoryId,
      group.first().categoryName,
      group.first().categoryColor,
      amount,
      if (total > 0) amount.toDouble() / total else 0.0,
      group,
    )
  }.sortedWith(compareByDescending<CategoryReport> { it.amountMinor }.thenBy { it.name }.thenBy { it.id })

private fun buildBuckets(
  dated: List<Pair<TransactionListItem, LocalDate>>,
  chartWindow: ReportWindow,
  kind: ReportPeriodKind,
): List<ReportBucket> {
  if (kind == ReportPeriodKind.YEAR) {
    return (1..12).mapNotNull { month ->
      val start = LocalDate.of(chartWindow.start.year, month, 1)
      if (start.isAfter(chartWindow.end)) return@mapNotNull null
      val window = ReportWindow(start, minOf(YearMonth.from(start).atEndOfMonth(), chartWindow.end))
      val transactions = dated.filter { (_, date) -> date in window }.map { it.first }
      ReportBucket(window, transactions.sumOf { it.transaction.amount }, transactions.size, categoryReports(transactions, transactions.sumOf { it.transaction.amount }))
    }
  }

  // Month reports keep an exact daily bar. Longer custom/all-time ranges are
  // grouped to at most twelve selectable windows so touch targets stay usable.
  val bucketDays = if (kind == ReportPeriodKind.MONTH) 1L else (chartWindow.days + 11) / 12
  return (0L until (chartWindow.days + bucketDays - 1) / bucketDays).map { index ->
    val start = chartWindow.start.plusDays(index * bucketDays)
    val window = ReportWindow(start, minOf(start.plusDays(bucketDays - 1), chartWindow.end))
    val transactions = dated.filter { (_, date) -> date in window }.map { it.first }
    val total = transactions.sumOf { it.transaction.amount }
    ReportBucket(window, total, transactions.size, categoryReports(transactions, total))
  }
}
