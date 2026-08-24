package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.repositories.TransactionListItem

enum class TypeFilter(val label: String) { ALL("All"), INCOME("Income"), EXPENSE("Expense") }

enum class SortOrder(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest"), SMALLEST("Smallest") }

/**
 * Pure filter state + logic for the Transactions list. No Compose
 * dependencies, so it is unit-testable on the JVM.
 */
data class TransactionFilterState(
  val type: TypeFilter = TypeFilter.ALL,
  val categoryIds: Set<String> = emptySet(),
  val accountIds: Set<String> = emptySet(),
  val currencyIds: Set<String> = emptySet(),
  val sort: SortOrder = SortOrder.NEWEST,
  val dateFrom: String? = null,
  val dateTo: String? = null,
  val search: String = "",
) {
  fun isDefault(): Boolean =
    type == TypeFilter.ALL &&
      categoryIds.isEmpty() &&
      accountIds.isEmpty() &&
      currencyIds.isEmpty() &&
      sort == SortOrder.NEWEST &&
      dateFrom == null &&
      dateTo == null &&
      search.isBlank()
}

object TransactionFiltering {
  /** Number of active filter groups, for the FILTERS badge. */
  fun activeCount(f: TransactionFilterState): Int {
    var count = 0
    if (f.type != TypeFilter.ALL) count++
    if (f.categoryIds.isNotEmpty()) count++
    if (f.accountIds.isNotEmpty()) count++
    // Currency is a context selection (default currency is always selected),
    // not a filter, so it does not count toward the badge.
    if (f.sort != SortOrder.NEWEST) count++
    if (f.dateFrom != null || f.dateTo != null) count++
    if (f.search.isNotBlank()) count++
    return count
  }

  fun apply(items: List<TransactionListItem>, f: TransactionFilterState): List<TransactionListItem> =
    items
      .asSequence()
      .filter { item ->
        val tx = item.transaction
        (f.type == TypeFilter.ALL || tx.type.name == f.type.name) &&
          (f.categoryIds.isEmpty() || tx.categoryId in f.categoryIds) &&
          (f.accountIds.isEmpty() || tx.accountId in f.accountIds) &&
          (f.currencyIds.isEmpty() || tx.currencyId in f.currencyIds) &&
          (f.dateFrom == null || tx.date >= f.dateFrom) &&
          (f.dateTo == null || tx.date <= f.dateTo) &&
          (f.search.isBlank() ||
            tx.title?.contains(f.search, ignoreCase = true) == true ||
            tx.notes?.contains(f.search, ignoreCase = true) == true ||
            item.categoryName.contains(f.search, ignoreCase = true))
      }
      .sortedWith(sortComparator(f.sort))
      .toList()

  /** Groups preserving sort order; each pair is (date, items). */
  fun groupByDate(items: List<TransactionListItem>): List<Pair<String, List<TransactionListItem>>> =
    items.groupBy { it.transaction.date }.toList()

  private fun sortComparator(sort: SortOrder): Comparator<TransactionListItem> =
    when (sort) {
      SortOrder.NEWEST ->
        compareByDescending<TransactionListItem> { it.transaction.date }
          .thenByDescending { it.transaction.createdAt }
      SortOrder.OLDEST ->
        compareBy<TransactionListItem> { it.transaction.date }
          .thenBy { it.transaction.createdAt }
      SortOrder.LARGEST -> compareByDescending<TransactionListItem> { it.transaction.amount }
      SortOrder.SMALLEST -> compareBy<TransactionListItem> { it.transaction.amount }
    }
}
