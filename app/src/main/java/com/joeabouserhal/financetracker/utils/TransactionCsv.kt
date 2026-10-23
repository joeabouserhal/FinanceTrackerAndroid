package com.joeabouserhal.financetracker.utils

import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import java.math.BigDecimal

object TransactionCsv {
  /** Quote every text cell and neutralize spreadsheet formulas in user-entered fields. */
  internal fun cell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n') ||
      value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
  }

  fun write(items: List<TransactionListItem>, output: Appendable) {
    output.append("Date,Type,Amount,Currency,From account,To account,Category,Title,Notes\r\n")
    items.forEach { item ->
      val tx = item.transaction
      val amount = BigDecimal.valueOf(tx.amount, 2).let {
        if (tx.type == TransactionType.EXPENSE || tx.type == TransactionType.GOAL) it.negate() else it
      }.toPlainString()
      output.append(listOf(cell(tx.date), cell(tx.type.name), amount, cell(item.currencyCode),
        cell(item.accountName.orEmpty()), cell(item.destinationAccountName.orEmpty()),
        cell(item.categoryName), cell(tx.title.orEmpty()), cell(tx.notes.orEmpty())).joinToString(","))
        .append("\r\n")
    }
  }
}
