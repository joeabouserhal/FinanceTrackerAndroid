package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.repositories.amountForAccount

data class AccountBalanceChange(val before: Long, val after: Long)

/** Editing first removes the old transfer's effect; a new transfer leaves currency totals unchanged. */
fun transferBalanceChanges(balances: Map<String, Long>, original: TransactionEntity?, source: String,
  destination: String, amount: Long): Pair<AccountBalanceChange, AccountBalanceChange> {
  require(source != destination && amount > 0)
  fun before(id: String) = Math.subtractExact(balances[id] ?: 0, original?.amountForAccount(id) ?: 0)
  val from = before(source)
  val to = before(destination)
  return AccountBalanceChange(from, Math.subtractExact(from, amount)) to
    AccountBalanceChange(to, Math.addExact(to, amount))
}
