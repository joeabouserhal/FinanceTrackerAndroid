package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType

/** Transfers change individual accounts, never the total money owned. */
fun TransactionEntity.netAmount(): Long = when (type) {
  TransactionType.INCOME -> amount
  TransactionType.EXPENSE, TransactionType.GOAL -> -amount
  TransactionType.TRANSFER -> 0L
}

fun TransactionEntity.amountForAccount(id: String): Long = when {
  deletedAt != null -> 0L
  type == TransactionType.TRANSFER && destinationAccountId == id -> amount
  accountId == id -> if (type == TransactionType.INCOME) amount else -amount
  else -> 0L
}

fun accountBalanceMap(transactions: List<TransactionEntity>, accounts: List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity> = emptyList()): Map<String, Long> {
  val balances = accounts.filter { it.deletedAt == null }.associate { it.id to it.openingBalanceMinor }.toMutableMap()
  transactions.filter { it.deletedAt == null }.forEach { tx ->
    tx.accountId?.let { balances[it] = (balances[it] ?: 0L) + tx.amountForAccount(it) }
    tx.destinationAccountId?.takeIf { tx.type == TransactionType.TRANSFER }?.let {
      balances[it] = (balances[it] ?: 0L) + tx.amount
    }
  }
  return balances
}
