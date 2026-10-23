package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType

/** Only editable fields are copied. IDs, sync metadata and automation lineage cannot leak into a new entry. */
data class TransactionDraft(val type: TransactionType, val amount: Long, val currencyId: String,
  val categoryId: String?, val accountId: String?, val destinationAccountId: String?, val date: String,
  val title: String, val notes: String) {
  companion object {
    fun duplicate(original: TransactionEntity, today: String): TransactionDraft {
      require(original.deletedAt == null) { "The original transaction is no longer available." }
      java.time.LocalDate.parse(today)
      return TransactionDraft(if (original.type == TransactionType.GOAL) TransactionType.EXPENSE else original.type,
        original.amount, original.currencyId, original.categoryId, original.accountId, original.destinationAccountId,
        today, original.title.orEmpty(), original.notes.orEmpty())
    }
  }
}
