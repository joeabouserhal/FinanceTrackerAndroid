package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.transactions.TransactionRow
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.Money
import java.time.YearMonth
import kotlinx.coroutines.flow.combine

/** One account: balance, current-month activity, and its transactions. */
@Composable
fun AccountDetailScreen(
  accountId: String,
  onBack: () -> Unit,
  onEditTransaction: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  val state by remember(ownerId, accountId) {
    combine(
      container.accountRepository.observeActive(ownerId),
      container.accountRepository.observeArchived(ownerId),
      container.currencyRepository.observeAll(ownerId),
      container.categoryRepository.observeByType(ownerId, TransactionType.INCOME),
      container.categoryRepository.observeByType(ownerId, TransactionType.EXPENSE),
    ) { active, archived, currencies, incomeCategories, expenseCategories ->
      active to listOf(archived, currencies, incomeCategories, expenseCategories)
    }
      .combine(container.transactionRepository.observeAll(ownerId)) { lookup, transactions ->
        val (active, rest) = lookup
        val archived = rest[0] as List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity>
        val currencies = rest[1] as List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>
        val incomeCategories = rest[2] as List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>
        val expenseCategories = rest[3] as List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>
        val account = (active + archived).firstOrNull { it.id == accountId }
        val currency = account?.let { a -> currencies.firstOrNull { it.id == a.currencyId } }
        val accountTxs = transactions.filter { it.accountId == accountId }
        val balance = accountTxs.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
        val (from, to) = Dates.monthBounds(YearMonth.now())
        val monthIncome = accountTxs.filter { it.date >= from && it.date <= to && it.type == TransactionType.INCOME }.sumOf { it.amount }
        val monthExpense = accountTxs.filter { it.date >= from && it.date <= to && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val items = enrichTransactions(accountTxs, incomeCategories + expenseCategories, active + archived, currencies)
        AccountDetailState(account?.name ?: "Account", currency?.code ?: "", currency?.symbol ?: "", balance, monthIncome, monthExpense, items)
      }
  }.collectAsStateWithLifecycle(initialValue = AccountDetailState("", "", "", 0, 0, 0, emptyList()))

  Column(modifier.fillMaxSize().background(spec.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("< BACK", style = MaterialTheme.typography.labelMedium, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack))
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(state.accountName.uppercase(), style = MaterialTheme.typography.headlineLarge, color = spec.ink)
      Text("${state.currencyCode} ACCOUNT", style = MaterialTheme.typography.labelMedium, color = spec.muted)
    }

    DividerLine()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("BALANCE", style = MaterialTheme.typography.labelMedium, color = spec.muted)
      Text(
        Money.format(state.balanceMinor, state.currencySymbol, forceDecimals = true),
        style = MaterialTheme.typography.displaySmall,
        color = if (state.balanceMinor < 0) spec.expense else spec.ink,
      )
      Text("THIS MONTH", style = MaterialTheme.typography.labelMedium, color = spec.muted)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("+${Money.format(state.monthIncome, state.currencySymbol)}", style = MaterialTheme.typography.labelLarge, color = spec.income)
        Text("-${Money.format(state.monthExpense, state.currencySymbol)}", style = MaterialTheme.typography.labelLarge, color = spec.expense)
      }
    }

    DividerLine()

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Text("TRANSACTIONS", style = MaterialTheme.typography.labelMedium, color = spec.muted)
    }
    if (state.items.isEmpty()) {
      Text(
        "No transactions on this account yet.",
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    } else {
      LazyColumn(Modifier.weight(1f)) {
        items(state.items, key = { it.transaction.id }) { item ->
          TransactionRow(item = item, onPress = { onEditTransaction(item.transaction.id) })
        }
      }
    }
  }
}

private data class AccountDetailState(
  val accountName: String,
  val currencyCode: String,
  val currencySymbol: String,
  val balanceMinor: Long,
  val monthIncome: Long,
  val monthExpense: Long,
  val items: List<com.joeabouserhal.financetracker.data.repositories.TransactionListItem>,
)

@Composable
private fun DividerLine() {
  val spec = LocalThemeSpec.current
  androidx.compose.foundation.layout.Box(
    Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(spec.border.copy(alpha = 0.45f)),
  )
}
