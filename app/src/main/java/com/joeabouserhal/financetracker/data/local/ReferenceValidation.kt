package com.joeabouserhal.financetracker.data.local

import com.joeabouserhal.financetracker.data.local.entities.TransactionType

/** Called inside the mutation transaction so validation and writes see one snapshot. */
internal suspend fun AppDatabase.validateReferences(
  ownerId: String, currencyId: String?, accountId: String?, categoryId: String? = null,
  type: TransactionType? = null, allowArchivedAccount: Boolean = false,
) {
  currencyId?.let {
    val currency = currencyDao().getById(ownerId, it)
    require(currency != null && currency.deletedAt == null) { "Currency is unavailable" }
  }
  accountId?.let {
    val account = accountDao().getById(ownerId, it)
    require(account != null && account.deletedAt == null && (allowArchivedAccount || !account.archived)) { "Choose an active account" }
    require(currencyId != null && account.currencyId == currencyId) { "Account must use the selected currency" }
  }
  categoryId?.let {
    val category = categoryDao().getById(ownerId, it)
    require(category != null && category.deletedAt == null && (type == null || category.type == type)) { "Choose an available category for this transaction type" }
  }
}
