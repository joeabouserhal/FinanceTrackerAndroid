package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.parseHexColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.combine

/** Saveable filter state shared by the Transactions and Report pages. */
val TransactionFilterStateSaver = listSaver<TransactionFilterState, String>(
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

/**
 * The full filter panel (type / sort / date / category / currency / account),
 * shared by Transactions and Report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
  filters: TransactionFilterState,
  onFiltersChange: (TransactionFilterState) -> Unit,
  typeHint: TypeFilter? = null,
  showTypeRow: Boolean = true,
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
  // Categories follow the effective TYPE (explicit hint on the Report page,
  // otherwise the panel's own TYPE chips).
  val effectiveType = typeHint ?: filters.type
  val visibleCategories =
    allCategories.filter { c ->
      when (effectiveType) {
        TypeFilter.ALL -> true
        TypeFilter.INCOME -> c.type == TransactionType.INCOME
        TypeFilter.EXPENSE -> c.type == TransactionType.EXPENSE
      }
    }

  // When the Report view toggle changes type, drop stale category picks.
  LaunchedEffect(typeHint) {
    if (typeHint != null && typeHint != TypeFilter.ALL) {
      val allowed = allCategories.filter { it.type.name == typeHint.name }.map { it.id }.toSet()
      if (filters.categoryIds.any { it !in allowed }) {
        onFiltersChange(filters.copy(categoryIds = filters.categoryIds.intersect(allowed)))
      }
    }
  }

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
      if (showTypeRow) {
        ChipRow("TYPE") {
          TypeFilter.entries.forEach { tf ->
            BrChip(
              tf.label,
              selected = filters.type == tf,
              onClick = {
                // Prune category selections that belong to another type.
                val allowed =
                  allCategories.filter { tf == TypeFilter.ALL || it.type.name == tf.name }.map { it.id }.toSet()
                onFiltersChange(filters.copy(type = tf, categoryIds = filters.categoryIds.intersect(allowed)))
              },
            )
          }
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
      // then every category of the selected TYPE is shown in the slider.
      ChipRow("CATEGORY") {
        BrChip("ALL", selected = filters.categoryIds.isEmpty(), onClick = { onFiltersChange(filters.copy(categoryIds = emptySet())) })
        BrChip("CUSTOM", selected = filters.categoryIds.isNotEmpty(), onClick = { showCategoryModal = true })
        visibleCategories.forEach { c ->
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
          visibleCategories
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

fun parseCategoryColor(hex: String): Color = parseHexColor(hex)
