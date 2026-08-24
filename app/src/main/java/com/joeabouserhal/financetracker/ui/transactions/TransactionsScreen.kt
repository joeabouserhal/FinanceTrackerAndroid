package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.ExpandableFab
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.EmptyState
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.combine


@Composable
fun TransactionsScreen(
  onAddTransaction: () -> Unit,
  onAddFromPreset: () -> Unit,
  onEditTransaction: (String) -> Unit,
) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filters by rememberSaveable(stateSaver = TransactionFilterStateSaver) { mutableStateOf(TransactionFilterState()) }
  var filtersOpen by rememberSaveable { mutableStateOf(false) }

  val items by remember(ownerId) {
    combine(
      container.transactionRepository.observeAll(ownerId),
      container.categoryRepository.observeByType(ownerId, TransactionType.INCOME),
      container.categoryRepository.observeByType(ownerId, TransactionType.EXPENSE),
      container.accountRepository.observeActive(ownerId),
      container.currencyRepository.observeAll(ownerId),
    ) { txs, incomeCategories, expenseCategories, accounts, currencies ->
      enrichTransactions(txs, incomeCategories + expenseCategories, accounts, currencies)
    }
  }.collectAsStateWithLifecycle(initialValue = emptyList())

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  // Normalize stale saved filter combos (currency must exist, accounts must
  // belong to the selected currency). Default selection is ALL.
  LaunchedEffect(currencies, accounts) {
    if (currencies.isEmpty()) return@LaunchedEffect
    val selectedIds =
      if (filters.currencyIds.all { id -> currencies.any { it.id == id } }) filters.currencyIds else emptySet()
    val visibleAccountIds =
      accounts.filter { selectedIds.isEmpty() || it.currencyId in selectedIds }.map { it.id }.toSet()
    val accountIds = filters.accountIds.intersect(visibleAccountIds)
    if (selectedIds != filters.currencyIds || accountIds != filters.accountIds) {
      filters = filters.copy(currencyIds = selectedIds, accountIds = accountIds)
    }
  }

  val visible = remember(items, filters) { TransactionFiltering.apply(items, filters) }
  val grouped = remember(visible) { TransactionFiltering.groupByDate(visible) }
  val activeCount = TransactionFiltering.activeCount(filters)

  Column(Modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Transactions")

    Column(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        BrTextField(
          value = filters.search,
          onValueChange = { filters = filters.copy(search = it) },
          label = "SEARCH",
          modifier = Modifier.weight(1f),
        )
        if (filters.search.isNotBlank()) {
          Text(
            "✕",
            style = MaterialTheme.typography.labelLarge,
            color = spec.muted,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clickable { filters = filters.copy(search = "") },
          )
        }
      }
      BrButton(
        text = if (activeCount > 0) "FILTERS ($activeCount)" else "FILTERS",
        onClick = { filtersOpen = !filtersOpen },
        style = if (filtersOpen || activeCount > 0) BrButtonStyle.SOLID else BrButtonStyle.OUTLINE,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    AnimatedVisibility(
      visible = filtersOpen,
      enter = expandVertically(tween(200)) + fadeIn(tween(200)),
      exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
      FilterPanel(
        filters = filters,
        onFiltersChange = { filters = it },
      )
    }

    Box(Modifier.weight(1f)) {
      if (visible.isEmpty()) {
        EmptyState(
          message = if (items.isEmpty()) "No transactions yet. Tap + to add one." else "No transactions match your filters.",
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
      } else {
        LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
          grouped.forEach { (date, groupItems) ->
            item(key = "header-$date") {
              DateHeader(date, groupItems)
            }
            items(groupItems, key = { it.transaction.id }) { item ->
              TransactionRow(
                item = item,
                onPress = { onEditTransaction(item.transaction.id) },
                modifier = Modifier.animateItem(),
              )
            }
          }
        }
      }
      ExpandableFab(
        onAddTransaction = onAddTransaction,
        onAddFromPreset = onAddFromPreset,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
    }
  }
}

@Composable
private fun DateHeader(date: String, groupItems: List<TransactionListItem>) {
  val spec = LocalThemeSpec.current
  val label = formatDateLabel(date)
  Column(Modifier.fillMaxWidth().background(spec.background).padding(vertical = 8.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = spec.muted)
  }
}

private fun formatDateLabel(iso: String): String =
  try {
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).format(LocalDate.parse(iso)).uppercase()
  } catch (_: Exception) {
    iso.uppercase()
  }

