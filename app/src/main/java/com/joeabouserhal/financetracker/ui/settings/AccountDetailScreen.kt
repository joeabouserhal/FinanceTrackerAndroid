package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.data.repositories.amountForAccount
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.transactions.TransactionRow
import com.joeabouserhal.financetracker.ui.transactions.TransactionDashedDivider
import com.joeabouserhal.financetracker.ui.components.ManagementPage
import com.joeabouserhal.financetracker.ui.components.ManagementSection
import com.joeabouserhal.financetracker.ui.components.ManagementEmptyState
import com.joeabouserhal.financetracker.ui.components.compactCurrencyText
import com.joeabouserhal.financetracker.utils.Dates
import java.time.YearMonth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

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
      Triple(active + archived, currencies, incomeCategories + expenseCategories)
    }
      .combine(container.transactionRepository.observeForAccount(ownerId, accountId)) { lookup, transactions ->
        val (accounts, currencies, categories) = lookup
        val account = accounts.firstOrNull { it.id == accountId }
        val currency = account?.let { a -> currencies.firstOrNull { it.id == a.currencyId } }
        val accountTxs = transactions.filter { it.accountId == accountId || it.destinationAccountId == accountId }
        val balance = (account?.openingBalanceMinor ?: 0L) + accountTxs.sumOf { it.amountForAccount(accountId) }
        val (from, to) = Dates.monthBounds(YearMonth.now())
        val monthIncome = accountTxs.filter { it.date >= from && it.date <= to && it.type == TransactionType.INCOME }.sumOf { it.amount }
        val monthExpense = accountTxs.filter { it.date >= from && it.date <= to && (it.type == TransactionType.EXPENSE || it.type == TransactionType.GOAL) }.sumOf { it.amount }
        val items = enrichTransactions(accountTxs, categories, accounts, currencies)
        AccountDetailState(account?.name ?: "Account", currency?.code ?: "", currency?.symbol ?: "", balance, monthIncome, monthExpense, items,
          openingBalanceMinor = account?.openingBalanceMinor ?: 0L)
      }.flowOn(kotlinx.coroutines.Dispatchers.Default)
  }.collectAsStateWithLifecycle(initialValue = AccountDetailState("", "", "", 0, 0, 0, emptyList()))

  AccountDetailContent(state, onBack, onEditTransaction, modifier)
}

@Composable
internal fun AccountDetailContent(
  state: AccountDetailState,
  onBack: () -> Unit,
  onEditTransaction: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  ManagementPage(state.accountName.ifBlank { "Account" }, "${state.currencyCode} · Account overview", onBack, modifier, listTag = "account-detail-list") {
    item("balance") {
      Column(Modifier.fillMaxWidth().background(spec.surface).border(1.dp, spec.border).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CURRENT BALANCE", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(state.balanceMinor, state.currencySymbol), style = MaterialTheme.typography.headlineMedium,
          color = if (state.balanceMinor < 0) spec.expense else spec.ink)
        if (state.openingBalanceMinor != 0L) Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
          Text("OPENING BALANCE", style = MaterialTheme.typography.labelSmall, color = spec.muted)
          com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(state.openingBalanceMinor, state.currencySymbol),
            style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
        TransactionDashedDivider()
        Text("THIS MONTH", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EARNED", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(state.monthIncome, state.currencySymbol, "+ "), style = MaterialTheme.typography.titleMedium, color = spec.income)
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SPENT", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(state.monthExpense, state.currencySymbol, "− "), style = MaterialTheme.typography.titleMedium, color = spec.expense)
          }
        }
      }
    }
    item("transactions-heading") { ManagementSection("Transactions", state.items.size) }
    if (state.items.isEmpty()) item("empty") {
      ManagementEmptyState("No activity yet", "Transactions recorded against this account will appear here.")
    }
    itemsIndexed(state.items.sortedByDescending { it.transaction.date }, key = { _, item -> item.transaction.id }) { index, item ->
      Column(Modifier.fillMaxWidth().background(spec.surface)) {
        if (index > 0) TransactionDashedDivider(Modifier.padding(horizontal = 12.dp))
        TransactionRow(item, { onEditTransaction(item.transaction.id) })
      }
    }
  }
}

internal data class AccountDetailState(
  val accountName: String,
  val currencyCode: String,
  val currencySymbol: String,
  val balanceMinor: Long,
  val monthIncome: Long,
  val monthExpense: Long,
  val items: List<com.joeabouserhal.financetracker.data.repositories.TransactionListItem>,
  val openingBalanceMinor: Long = 0,
)
