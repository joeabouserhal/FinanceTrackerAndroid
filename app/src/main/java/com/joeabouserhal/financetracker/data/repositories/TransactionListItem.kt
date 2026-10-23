package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType

/** A transaction enriched with the display fields the row needs. */
data class TransactionListItem(
  val transaction: TransactionEntity,
  val categoryName: String,
  val categoryColor: String,
  val currencySymbol: String,
  val accountName: String?,
  val destinationAccountName: String? = null,
  val currencyCode: String = "",
)

fun enrichTransactions(
  transactions: List<TransactionEntity>,
  categories: List<CategoryEntity>,
  accounts: List<AccountEntity>,
  currencies: List<CurrencyEntity>,
): List<TransactionListItem> {
  val categoryById = categories.associateBy { it.id }
  val accountById = accounts.associateBy { it.id }
  val currencyById = currencies.associateBy { it.id }
  return transactions.map { tx ->
    TransactionListItem(
      transaction = tx,
      categoryName = if (tx.type == TransactionType.TRANSFER) "Transfer" else categoryById[tx.categoryId]?.name ?: "Other",
      categoryColor = categoryById[tx.categoryId]?.color ?: "#77746C",
      currencySymbol = currencyById[tx.currencyId]?.symbol ?: "",
      accountName = tx.accountId?.let { accountById[it]?.name },
      destinationAccountName = tx.destinationAccountId?.let { accountById[it]?.name },
      currencyCode = currencyById[tx.currencyId]?.code.orEmpty(),
    )
  }
}
