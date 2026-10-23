package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem

enum class TypeFilter(val label: String) { ALL("All"), INCOME("Income"), EXPENSE("Expense"), TRANSFER("Transfer") }

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
  /** Bound values only; identifiers and ordering are selected from fixed app enums. */
  fun pageQuery(ownerId: String, f: TransactionFilterState, limit: Int): androidx.sqlite.db.SupportSQLiteQuery {
    val clauses = mutableListOf("owner_id=?", "deleted_at IS NULL")
    val args = mutableListOf<Any>(ownerId)
    when (f.type) {
      TypeFilter.ALL -> Unit
      TypeFilter.EXPENSE -> clauses += "type IN ('EXPENSE','GOAL')"
      TypeFilter.INCOME -> clauses += "type='INCOME'"
      TypeFilter.TRANSFER -> clauses += "type='TRANSFER'"
    }
    fun ids(column: String, values: Set<String>) {
      if (values.isNotEmpty()) { clauses += "$column IN (${values.joinToString(",") { "?" }})"; args.addAll(values.sorted()) }
    }
    ids("category_id", f.categoryIds)
    ids("currency_id", f.currencyIds)
    if (f.accountIds.isNotEmpty()) {
      val placeholders = f.accountIds.joinToString(",") { "?" }
      clauses += "(account_id IN ($placeholders) OR destination_account_id IN ($placeholders))"
      args.addAll(f.accountIds.sorted()); args.addAll(f.accountIds.sorted())
    }
    f.dateFrom?.let { clauses += "date>=?"; args += it }
    f.dateTo?.let { clauses += "date<=?"; args += it }
    val order = when (f.sort) {
      SortOrder.NEWEST -> "date DESC,created_at DESC,id DESC"
      SortOrder.OLDEST -> "date ASC,created_at ASC,id ASC"
      SortOrder.LARGEST -> "amount DESC,date DESC,created_at DESC,id DESC"
      SortOrder.SMALLEST -> "amount ASC,date DESC,created_at DESC,id DESC"
    }
    // Unicode-aware search uses the existing Kotlin matcher off the UI thread.
    // Do not limit before searching: an old matching entry must remain discoverable.
    val bound = if (f.search.isBlank()) " LIMIT ?" else ""
    if (bound.isNotEmpty()) args += limit.coerceAtLeast(1)
    return androidx.sqlite.db.SimpleSQLiteQuery("SELECT * FROM transactions WHERE ${clauses.joinToString(" AND ")} ORDER BY $order$bound", args.toTypedArray())
  }

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
        val typeMatches =
          when (f.type) {
            TypeFilter.ALL -> true
            TypeFilter.INCOME -> tx.type == TransactionType.INCOME
            // Goal withdrawals are money out of an account, so EXPENSE includes them.
            TypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE || tx.type == TransactionType.GOAL
            TypeFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
          }
        typeMatches &&
          (f.categoryIds.isEmpty() || tx.categoryId in f.categoryIds) &&
          (f.accountIds.isEmpty() || tx.accountId in f.accountIds || tx.destinationAccountId in f.accountIds) &&
          (f.currencyIds.isEmpty() || tx.currencyId in f.currencyIds) &&
          (f.dateFrom == null || tx.date >= f.dateFrom) &&
          (f.dateTo == null || tx.date <= f.dateTo) &&
          matchesSearch(item, f.search)
      }
      .sortedWith(sortComparator(f.sort))
      .toList()

  /** Each word can match a different field: e.g. "cash coffee 12.50". */
  internal fun matchesSearch(item: TransactionListItem, query: String): Boolean {
    val tx = item.transaction
    val fields = listOfNotNull(tx.title, tx.notes, item.categoryName, item.accountName,
      item.destinationAccountName, item.currencyCode, item.currencySymbol, tx.type.name,
      if (tx.type == TransactionType.GOAL) "expense goal" else null, tx.date)
    val decimal = java.math.BigDecimal.valueOf(tx.amount, 2).toPlainString()
    val short = java.math.BigDecimal.valueOf(tx.amount, 2).stripTrailingZeros().toPlainString()
    val signed = (if (tx.type == TransactionType.INCOME) "+" else if (tx.type == TransactionType.TRANSFER) "" else "-") + decimal
    val amountFields = listOf(decimal, short, signed, decimal.replace('.', ','),
      com.joeabouserhal.financetracker.utils.Money.format(tx.amount, item.currencySymbol),
      item.currencySymbol + decimal)
    return query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.all { word ->
      fields.any { it.contains(word, ignoreCase = true) } ||
        amountFields.any { it.contains(word, ignoreCase = true) }
    }
  }

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
