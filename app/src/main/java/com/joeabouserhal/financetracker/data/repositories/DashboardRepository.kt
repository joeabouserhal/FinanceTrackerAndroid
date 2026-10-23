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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

private data class DashboardLedger(
  val nets: List<com.joeabouserhal.financetracker.data.local.dao.AccountNetRow>,
  val usedCurrencies: List<String>, val recent: List<TransactionEntity>, val month: List<TransactionEntity>,
)

data class AccountBalance(
  val account: AccountEntity,
  val currency: CurrencyEntity,
  val balanceMinor: Long,
)

data class CurrencyBalance(
  val currency: CurrencyEntity,
  val totalMinor: Long,
  val accounts: List<AccountBalance>,
  /** True when at least one historical transaction used this currency. */
  val hasActivity: Boolean = false,
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
      combine(transactionDao.observeAccountNets(ownerId), transactionDao.observeUsedCurrencies(ownerId),
        transactionDao.observeRecent(ownerId, recentLimit), transactionDao.observeBetween(ownerId, monthFrom, monthTo), ::DashboardLedger),
    ) { currencies, accounts, categories, ledger ->
      buildDashboard(currencies, accounts, categories, ledger)
    }.flowOn(Dispatchers.Default)

  private fun buildDashboard(
    currencies: List<CurrencyEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    ledger: DashboardLedger,
  ): DashboardData {
    val currencyById = currencies.associateBy { it.id }

    // Single-pass per-account all-time nets (was O(accounts × transactions)
    // of full-list filters before this phase).
    val netByAccount = ledger.nets.associate { it.accountId to it.balanceMinor }
    val accountBalances =
      accounts.mapNotNull { account ->
        val currency = currencyById[account.currencyId] ?: return@mapNotNull null
        AccountBalance(account, currency, netByAccount[account.id] ?: 0L)
      }
    val currenciesWithActivity = ledger.usedCurrencies.toSet()

    val balances =
      currencies.map { currency ->
        val perAccount = accountBalances.filter { it.currency.id == currency.id }
        CurrencyBalance(
          currency = currency,
          totalMinor = perAccount.sumOf { it.balanceMinor },
          accounts = perAccount,
          hasActivity = currency.id in currenciesWithActivity || perAccount.any { it.account.openingBalanceMinor != 0L },
        )
      }
        .sortedBy { if (it.currency.isDefault) 0 else 1 }

    // Single-pass per-currency monthly activity.
    val incomeByCurrency = HashMap<String, Long>()
    val expenseByCurrency = HashMap<String, Long>()
    for (t in ledger.month) {
      if (t.type == TransactionType.TRANSFER) continue
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

    val recent = enrichTransactions(ledger.recent, categories, accounts, currencies)

    return DashboardData(balances, monthly, recent)
  }

}
