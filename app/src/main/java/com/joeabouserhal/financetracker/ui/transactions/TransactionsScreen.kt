package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
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
  val scope = rememberCoroutineScope()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filters by rememberSaveable(stateSaver = TransactionFilterStateSaver) { mutableStateOf(TransactionFilterState()) }
  var filtersOpen by rememberSaveable { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<TransactionListItem?>(null) }

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

  Box(Modifier.fillMaxSize().background(spec.background)) {
    Column(Modifier.fillMaxSize()) {
      ScreenHeader(title = "Transactions")

    Column(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BrTextField(
          value = filters.search,
          onValueChange = { filters = filters.copy(search = it) },
          label = "SEARCH",
          modifier = Modifier.weight(1f),
          leadingIconRes = R.drawable.ic_search,
          trailingIconRes = if (filters.search.isNotBlank()) R.drawable.ic_close else null,
          trailingIconDescription = "Clear search",
          onTrailingIconClick = if (filters.search.isNotBlank()) ({ filters = filters.copy(search = "") }) else null,
        )
        BrButton(
          text = if (activeCount > 0) "FILTER $activeCount" else "FILTER",
          onClick = { filtersOpen = !filtersOpen },
          style = if (filtersOpen || activeCount > 0) BrButtonStyle.SOLID else BrButtonStyle.OUTLINE,
          compact = true,
          fillWidth = false,
          minHeight = 56.dp,
          modifier = Modifier.offset(y = 4.dp),
        )
      }
    }

    AnimatedVisibility(
      visible = filtersOpen,
      enter = expandVertically(tween(200), expandFrom = Alignment.Top) + fadeIn(tween(180)),
      exit = shrinkVertically(tween(150), shrinkTowards = Alignment.Top) + fadeOut(tween(130)),
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
            itemsIndexed(groupItems, key = { _, item -> item.transaction.id }) { index, item ->
              Column(Modifier.animateItem()) {
                SwipeRevealRow(onDelete = { deleteTarget = item }) {
                  TransactionRow(
                    item = item,
                    onPress = { onEditTransaction(item.transaction.id) },
                  )
                }
                if (index < groupItems.lastIndex) {
                  TransactionDashedDivider()
                }
              }
            }
          }
        }
      }
    }
    }
    // Keep the action anchored when collapsed, but remove it entirely while
    // filters are open so it cannot cover the last filter row.
    AnimatedVisibility(
      visible = !filtersOpen,
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      enter = fadeIn(tween(120)),
      exit = fadeOut(tween(90)),
    ) {
      ExpandableFab(
        onAddTransaction = onAddTransaction,
        onAddFromPreset = onAddFromPreset,
      )
    }
  }

  deleteTarget?.let { item ->
    val tx = item.transaction
    val amount = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
    BrDialog(
      title = "DELETE TRANSACTION?",
      onDismiss = { deleteTarget = null },
      confirmText = "DELETE",
      onConfirm = {
        val target = deleteTarget
        deleteTarget = null
        scope.launch {
          target?.let { container.transactionRepository.remove(ownerId, it.transaction.id) }
        }
      },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          tx.title?.takeIf { it.isNotBlank() } ?: item.categoryName,
          style = MaterialTheme.typography.bodyMedium,
          color = spec.ink,
        )
        Text(
          "${tx.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${formatDateLabel(tx.date)}",
          style = MaterialTheme.typography.labelSmall,
          color = spec.muted,
        )
        Text(
          Money.format(amount, item.currencySymbol),
          style = MaterialTheme.typography.titleLarge,
          color = if (amount < 0) spec.expense else spec.income,
        )
        Text(
          if (tx.type == TransactionType.GOAL)
            "This also un-completes its goal and removes the goal's other withdrawals."
          else "This removes it permanently.",
          style = MaterialTheme.typography.bodySmall,
          color = spec.muted,
        )
      }
    }
  }
}

/** Left-swipe reveals a DELETE action behind the row; nothing is removed
 * until the caller confirms. */
private enum class RevealState { Closed, Open }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRevealRow(
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val actionWidthPx = with(density) { 88.dp.toPx() }

  val state =
    remember {
      AnchoredDraggableState<RevealState>(initialValue = RevealState.Closed).apply {
        updateAnchors(
          DraggableAnchors {
            RevealState.Closed at 0f
            RevealState.Open at -actionWidthPx
          },
        )
      }
    }

  Box(modifier) {
    // Revealed action strip, behind the row's right edge: red 88dp-wide area
    // with a thin separator at its left edge and DELETE centered inside it.
    Box(
      Modifier
        .matchParentSize()
        .background(spec.expense),
      contentAlignment = Alignment.CenterEnd,
    ) {
      Box(
        Modifier
          .fillMaxHeight()
          .width(88.dp),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(2.dp)
            .background(spec.onAccent.copy(alpha = 0.85f)),
        )
        Text(
          "DELETE",
          style = MaterialTheme.typography.labelMedium,
          color = spec.onAccent,
          modifier =
            Modifier
              .minimumInteractiveComponentSize()
              .clickable {
                scope.launch { state.animateTo(RevealState.Closed) }
                onDelete()
              }
              .padding(4.dp),
        )
      }
    }

    // Foreground row, slides left to reveal the action.
    Box(
      Modifier
        .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
        .anchoredDraggable(state, Orientation.Horizontal)
        .background(spec.background),
    ) {
      content()
    }
  }
}

@Composable
private fun DateHeader(date: String, groupItems: List<TransactionListItem>) {
  val spec = LocalThemeSpec.current
  val label = formatDateLabel(date)
  Row(
    Modifier.fillMaxWidth().background(spec.background).padding(top = 14.dp, bottom = 7.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = spec.ink)
    Text("${groupItems.size} ITEM${if (groupItems.size == 1) "" else "S"}", style = MaterialTheme.typography.labelSmall, color = spec.muted)
  }
}

private fun formatDateLabel(iso: String): String =
  try {
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).format(LocalDate.parse(iso)).uppercase()
  } catch (_: Exception) {
    iso.uppercase()
  }
