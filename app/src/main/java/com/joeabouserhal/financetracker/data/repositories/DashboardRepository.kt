package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.dao.AccountDao
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.CurrencyDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AccountBalance(
  val account: AccountEntity,
  val currency: CurrencyEntity,
  val balanceMinor: Long,
)

data class CurrencyBalance(
  val currency: CurrencyEntity,
  val totalMinor: Long,
  val accounts: List<AccountBalance>,
)

data class MonthlyActivity(
  val currency: CurrencyEntity,
  val incomeMinor: Long,
  val expenseMinor: Long,
)

data class DashboardData(
  val balances: List<CurrencyBalance>,
  val monthly: List<MonthlyActivity>,
  val recent: List<TransactionListItem>,
)

class DashboardRepository(
  private val currencyDao: CurrencyDao,
  private val accountDao: AccountDao,
  private val categoryDao: CategoryDao,
  private val transactionDao: TransactionDao,
) {
  fun observe(
    ownerId: String,
    monthFrom: String,
    monthTo: String,
    recentLimit: Int = 5,
  ): Flow<DashboardData> =
    combine(
      currencyDao.observeAll(ownerId),
      accountDao.observeActive(ownerId),
      categoryDao.observeAll(ownerId),
      transactionDao.observeAll(ownerId),
      transactionDao.observeBetween(ownerId, monthFrom, monthTo),
    ) { currencies, accounts, categories, allTransactions, monthTransactions ->
      buildDashboard(currencies, accounts, categories, allTransactions, monthTransactions, recentLimit)
    }

  private fun buildDashboard(
    currencies: List<CurrencyEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    allTransactions: List<TransactionEntity>,
    monthTransactions: List<TransactionEntity>,
    recentLimit: Int,
  ): DashboardData {
    val currencyById = currencies.associateBy { it.id }

    // Single-pass per-account all-time nets (was O(accounts × transactions)
    // of full-list filters before this phase).
    val netByAccount = HashMap<String, Long>()
    for (t in allTransactions) {
      t.accountId?.let { accountId -> netByAccount[accountId] = (netByAccount[accountId] ?: 0L) + signed(t) }
    }
    val accountBalances =
      accounts.mapNotNull { account ->
        val currency = currencyById[account.currencyId] ?: return@mapNotNull null
        AccountBalance(account, currency, netByAccount[account.id] ?: 0L)
      }

    val balances =
      currencies.map { currency ->
        val perAccount = accountBalances.filter { it.currency.id == currency.id }
        CurrencyBalance(currency, perAccount.sumOf { it.balanceMinor }, perAccount)
      }
        .sortedBy { if (it.currency.isDefault) 0 else 1 }

    // Single-pass per-currency monthly activity.
    val incomeByCurrency = HashMap<String, Long>()
    val expenseByCurrency = HashMap<String, Long>()
    for (t in monthTransactions) {
      val map = if (t.type == TransactionType.INCOME) incomeByCurrency else expenseByCurrency
      map[t.currencyId] = (map[t.currencyId] ?: 0L) + t.amount
    }
    val monthly =
      currencies.mapNotNull { currency ->
        val income = incomeByCurrency[currency.id] ?: 0L
        val expense = expenseByCurrency[currency.id] ?: 0L
        if (income == 0L && expense == 0L) null else MonthlyActivity(currency, income, expense)
      }
        .sortedBy { if (it.currency.isDefault) 0 else 1 }

    val recent = enrichTransactions(allTransactions.take(recentLimit), categories, accounts, currencies)

    return DashboardData(balances, monthly, recent)
  }

  private fun signed(t: TransactionEntity): Long = if (t.type == TransactionType.INCOME) t.amount else -t.amount
}
