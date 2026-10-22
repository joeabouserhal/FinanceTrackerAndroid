package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.AccountEntity

/** Only distinct, active accounts can be used to start a new transfer. */
internal fun transferCurrencyIds(accounts: List<AccountEntity>): Set<String> = accounts
  .filter { !it.archived && it.deletedAt == null }
  .groupBy { it.currencyId }
  .filterValues { group -> group.map { it.id }.distinct().size >= 2 }
  .keys
