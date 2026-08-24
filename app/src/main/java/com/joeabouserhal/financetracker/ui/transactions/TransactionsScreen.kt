package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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
import com.joeabouserhal.financetracker.ui.components.BrFab
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.EmptyState
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.combine

private val TransactionFilterStateSaver = listSaver<TransactionFilterState, String>(
  save = { f ->
    listOf(
      f.type.name,
      f.sort.name,
      f.dateFrom ?: "",
      f.dateTo ?: "",
      f.search,
      f.categoryIds.joinToString(","),
      f.accountIds.joinToString(","),
      f.currencyIds.joinToString(","),
    )
  },
  restore = { l ->
    TransactionFilterState(
      type = TypeFilter.valueOf(l[0]),
      sort = SortOrder.valueOf(l[1]),
      dateFrom = l[2].ifEmpty { null },
      dateTo = l[3].ifEmpty { null },
      search = l[4],
      categoryIds = l[5].split(",").filter { it.isNotBlank() }.toSet(),
      accountIds = l[6].split(",").filter { it.isNotBlank() }.toSet(),
      currencyIds = l[7].split(",").filter { it.isNotBlank() }.toSet(),
    )
  },
)

@Composable
fun TransactionsScreen(
  onAddTransaction: () -> Unit,
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
      BrFab(
        onClick = onAddTransaction,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
  filters: TransactionFilterState,
  onFiltersChange: (TransactionFilterState) -> Unit,
) {
  val spec = LocalThemeSpec.current
  val container = rememberAppContainer()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  val allCategories by remember(ownerId) {
    combine(
      container.categoryRepository.observeByType(ownerId, TransactionType.INCOME),
      container.categoryRepository.observeByType(ownerId, TransactionType.EXPENSE),
    ) { a, b -> a + b }
  }.collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var showRangePicker by remember { mutableStateOf(false) }
  var showCategoryModal by remember { mutableStateOf(false) }
  var categorySearch by remember { mutableStateOf("") }
  var showAccountModal by remember { mutableStateOf(false) }
  var accountSearch by remember { mutableStateOf("") }

  val selectedCurrency = currencies.firstOrNull { it.id in filters.currencyIds }
  val visibleAccounts = accounts.filter { filters.currencyIds.isEmpty() || it.currencyId in filters.currencyIds }

  val todayIso = remember { LocalDate.now().toString() }
  val monthBounds = remember { Dates.monthBounds(YearMonth.now()) }
  val datePreset =
    when {
      filters.dateFrom == null && filters.dateTo == null -> "ALL"
      filters.dateFrom == todayIso && filters.dateTo == todayIso -> "TODAY"
      filters.dateFrom == monthBounds.first && filters.dateTo == monthBounds.second -> "THIS MONTH"
      else -> "CUSTOM"
    }

  Column(Modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
    // Type
    ChipRow("TYPE") {
      TypeFilter.entries.forEach { tf ->
        BrChip(tf.label, selected = filters.type == tf, onClick = { onFiltersChange(filters.copy(type = tf)) })
      }
    }
    // Sort
    ChipRow("SORT") {
      SortOrder.entries.forEach { so ->
        BrChip(so.label, selected = filters.sort == so, onClick = { onFiltersChange(filters.copy(sort = so)) })
      }
    }
    // Date: presets + custom range picker
    ChipRow("DATE") {
      BrChip("ALL", selected = datePreset == "ALL", onClick = { onFiltersChange(filters.copy(dateFrom = null, dateTo = null)) })
      BrChip("TODAY", selected = datePreset == "TODAY", onClick = { onFiltersChange(filters.copy(dateFrom = todayIso, dateTo = todayIso)) })
      BrChip("THIS MONTH", selected = datePreset == "THIS MONTH", onClick = { onFiltersChange(filters.copy(dateFrom = monthBounds.first, dateTo = monthBounds.second)) })
      BrChip("CUSTOM", selected = datePreset == "CUSTOM", onClick = { showRangePicker = true })
    }
    // Category: ALL clears, CUSTOM opens a searchable multi-select modal,
    // then every category is shown in the slider with its selected state.
    ChipRow("CATEGORY") {
      BrChip("ALL", selected = filters.categoryIds.isEmpty(), onClick = { onFiltersChange(filters.copy(categoryIds = emptySet())) })
      BrChip("CUSTOM", selected = filters.categoryIds.isNotEmpty(), onClick = { showCategoryModal = true })
      allCategories.forEach { c ->
        BrChip(
          c.name,
          selected = c.id in filters.categoryIds,
          colorDot = parseCategoryColor(c.color),
          onClick = { onFiltersChange(filters.copy(categoryIds = toggle(filters.categoryIds, c.id))) },
        )
      }
    }
    // Currency: ALL by default; tapping a currency selects it, tapping it
    // again returns to ALL.
    ChipRow("CURRENCY") {
      BrChip(
        "ALL",
        selected = filters.currencyIds.isEmpty(),
        onClick = { onFiltersChange(filters.copy(currencyIds = emptySet())) },
      )
      currencies.forEach { c ->
        val selected = c.id in filters.currencyIds
        BrChip(
          c.code,
          selected = selected,
          onClick = {
            val newIds = if (selected) emptySet() else setOf(c.id)
            val newVisible = accounts.filter { newIds.isEmpty() || it.currencyId in newIds }
            onFiltersChange(
              filters.copy(
                currencyIds = newIds,
                accountIds = filters.accountIds.intersect(newVisible.map { it.id }.toSet()),
              ),
            )
          },
        )
      }
    }
    // Account: ALL shows every account of the selected currency, CUSTOM opens
    // a search modal, then every account is shown in the slider.
    ChipRow("ACCOUNT · ${selectedCurrency?.code ?: "ALL"}") {
      BrChip("ALL", selected = filters.accountIds.isEmpty(), onClick = { onFiltersChange(filters.copy(accountIds = emptySet())) })
      BrChip("CUSTOM", selected = filters.accountIds.isNotEmpty(), onClick = { showAccountModal = true })
      if (visibleAccounts.isEmpty()) {
        Text("No accounts for this currency", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      } else {
        visibleAccounts.forEach { a ->
          BrChip(
            a.name,
            selected = a.id in filters.accountIds,
            onClick = { onFiltersChange(filters.copy(accountIds = toggle(filters.accountIds, a.id))) },
          )
        }
      }
    }
    if (TransactionFiltering.activeCount(filters) > 0) {
      Text(
        "CLEAR ALL FILTERS",
        style = MaterialTheme.typography.labelSmall,
        color = spec.accent,
        modifier = Modifier
          .clickable {
            onFiltersChange(
              filters.copy(
                type = TypeFilter.ALL,
                sort = SortOrder.NEWEST,
                dateFrom = null,
                dateTo = null,
                categoryIds = emptySet(),
                accountIds = emptySet(),
              ),
            )
          }
          .padding(vertical = 4.dp),
      )
    }
    }
    // Flat expanded panel: no tint, just a full-width rule underneath with breathing room.
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.45f)))
    Spacer(Modifier.height(10.dp))
  }

  // Custom date RANGE picker.
  if (showRangePicker) {
    val rangeState = rememberDateRangePickerState()
    DatePickerDialog(
      onDismissRequest = { showRangePicker = false },
      confirmButton = {
        TextButton(
          enabled = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
          onClick = {
            val from = rangeState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
            val to = rangeState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
            if (from != null && to != null) onFiltersChange(filters.copy(dateFrom = from, dateTo = to))
            showRangePicker = false
          },
        ) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("CANCEL") } },
    ) {
      DateRangePicker(state = rangeState)
    }
  }

  // Category modal: search + multi-select, "ALL" clears the selection.
  if (showCategoryModal) {
    BrDialog(
      title = "CATEGORIES",
      onDismiss = { showCategoryModal = false },
      confirmText = "DONE",
      onConfirm = { showCategoryModal = false },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrTextField(categorySearch, { categorySearch = it }, "SEARCH CATEGORIES")
        BrChip(
          "ALL",
          selected = filters.categoryIds.isEmpty(),
          onClick = { onFiltersChange(filters.copy(categoryIds = emptySet())) },
        )
        Column(
          Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          allCategories
            .filter { it.name.contains(categorySearch, ignoreCase = true) }
            .forEach { c ->
              val selected = c.id in filters.categoryIds
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable { onFiltersChange(filters.copy(categoryIds = toggle(filters.categoryIds, c.id))) }
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(Modifier.size(10.dp).background(parseCategoryColor(c.color)))
                Spacer(Modifier.width(8.dp))
                Text(c.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f))
                Text(if (selected) "✓" else "", style = MaterialTheme.typography.labelLarge, color = spec.accent)
              }
            }
        }
      }
    }
  }

  // Account modal: search + multi-select over the selected currency's accounts.
  if (showAccountModal) {
    BrDialog(
      title = "ACCOUNTS",
      onDismiss = { showAccountModal = false },
      confirmText = "DONE",
      onConfirm = { showAccountModal = false },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrTextField(accountSearch, { accountSearch = it }, "SEARCH ACCOUNTS")
        BrChip(
          "ALL",
          selected = filters.accountIds.isEmpty(),
          onClick = { onFiltersChange(filters.copy(accountIds = emptySet())) },
        )
        Column(
          Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          visibleAccounts
            .filter { it.name.contains(accountSearch, ignoreCase = true) }
            .forEach { a ->
              val selected = a.id in filters.accountIds
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable { onFiltersChange(filters.copy(accountIds = toggle(filters.accountIds, a.id))) }
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f))
                Text(if (selected) "✓" else "", style = MaterialTheme.typography.labelLarge, color = spec.accent)
              }
            }
        }
      }
    }
  }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      content()
    }
  }
}

private fun toggle(set: Set<String>, id: String): Set<String> = if (id in set) set - id else set + id

private fun parseCategoryColor(hex: String): Color =
  try { Color(android.graphics.Color.parseColor(hex)) } catch (_: IllegalArgumentException) { Color(0xFF77746C) }
