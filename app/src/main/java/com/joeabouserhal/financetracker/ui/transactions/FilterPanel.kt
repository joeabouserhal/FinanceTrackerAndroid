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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
  showDateRow: Boolean = true,
  showSortRow: Boolean = true,
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
  LaunchedEffect(typeHint, allCategories) {
    // Do not erase selected IDs while the local category stream is loading.
    if (allCategories.isEmpty()) return@LaunchedEffect
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

  val reportRefinementsOnly = !showTypeRow && !showDateRow && !showSortRow
  val activeCount = if (reportRefinementsOnly) {
    listOf(filters.categoryIds, filters.accountIds, filters.currencyIds).count { it.isNotEmpty() }
  } else TransactionFiltering.activeCount(filters)

  Column(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .background(spec.surface),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("REFINE RESULTS", style = MaterialTheme.typography.labelLarge, color = spec.ink)
          Text(
            if (activeCount == 0) "SHOWING DEFAULT VIEW" else "$activeCount FILTER${if (activeCount == 1) "" else "S"} ACTIVE",
            style = MaterialTheme.typography.labelSmall,
            color = if (activeCount == 0) spec.muted else spec.accent,
          )
        }
        if (activeCount > 0 || filters.currencyIds.isNotEmpty()) {
          Text(
            "RESET",
            style = MaterialTheme.typography.labelMedium,
            color = spec.accent,
            modifier =
              Modifier
                .clickable {
                  onFiltersChange(
                    filters.copy(
                      type = TypeFilter.ALL,
                      sort = SortOrder.NEWEST,
                      dateFrom = null,
                      dateTo = null,
                      search = "",
                      categoryIds = emptySet(),
                      accountIds = emptySet(),
                      currencyIds = emptySet(),
                    ),
                  )
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
          )
        }
      }

      Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.38f)))

      // Type remains hidden on Report, where its spending/earning toggle is
      // always visible above this drawer.
      if (showTypeRow) {
        ChipRow("TYPE") {
          TypeFilter.entries.forEach { tf ->
            BrChip(
              tf.label,
              selected = filters.type == tf,
              comfortable = true,
              onClick = {
                val allowed =
                  allCategories.filter { tf == TypeFilter.ALL || it.type.name == tf.name }.map { it.id }.toSet()
                onFiltersChange(filters.copy(type = tf, categoryIds = filters.categoryIds.intersect(allowed)))
              },
            )
          }
        }
      }

      // Date presets keep the most common choices one tap away.
      if (showDateRow) ChipRow("DATE", if (datePreset == "CUSTOM") "${filters.dateFrom} → ${filters.dateTo}" else null) {
        BrChip("ALL", selected = datePreset == "ALL", comfortable = true, onClick = { onFiltersChange(filters.copy(dateFrom = null, dateTo = null)) })
        BrChip("TODAY", selected = datePreset == "TODAY", comfortable = true, onClick = { onFiltersChange(filters.copy(dateFrom = todayIso, dateTo = todayIso)) })
        BrChip("THIS MONTH", selected = datePreset == "THIS MONTH", comfortable = true, onClick = { onFiltersChange(filters.copy(dateFrom = monthBounds.first, dateTo = monthBounds.second)) })
        BrChip("CUSTOM", selected = datePreset == "CUSTOM", comfortable = true, onClick = { showRangePicker = true })
      }

      // Currency is a single-select context; accounts are pruned whenever it changes.
      ChipRow("CURRENCY") {
        BrChip(
          "ALL",
          selected = filters.currencyIds.isEmpty(),
          comfortable = true,
          onClick = { onFiltersChange(filters.copy(currencyIds = emptySet())) },
        )
        currencies.forEach { c ->
          val selected = c.id in filters.currencyIds
          BrChip(
            c.code,
            selected = selected,
            comfortable = true,
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

      // Keep the full account and category sets directly in their sliders so
      // the common choices never require opening a second surface.
      val selectedAccountNames = visibleAccounts.filter { it.id in filters.accountIds }.map { it.name }
      ChipRow("ACCOUNT", selectedAccountNames.takeIf { it.isNotEmpty() }?.joinToString(" · ")) {
        BrChip("ALL", selected = filters.accountIds.isEmpty(), comfortable = true, onClick = { onFiltersChange(filters.copy(accountIds = emptySet())) })
        val duplicateNames = visibleAccounts.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        visibleAccounts.forEach { account ->
          val suffix = if (account.name in duplicateNames) currencies.firstOrNull { it.id == account.currencyId }?.code else null
          BrChip(
            account.name,
            selected = account.id in filters.accountIds,
            comfortable = true,
            suffix = suffix,
            onClick = { onFiltersChange(filters.copy(accountIds = toggle(filters.accountIds, account.id))) },
          )
        }
      }

      val selectedCategoryNames = visibleCategories.filter { it.id in filters.categoryIds }.map { it.name }
      ChipRow("CATEGORY", selectedCategoryNames.takeIf { it.isNotEmpty() }?.joinToString(" · ")) {
        BrChip("ALL", selected = filters.categoryIds.isEmpty(), comfortable = true, onClick = { onFiltersChange(filters.copy(categoryIds = emptySet())) })
        BrChip(
          "CUSTOM",
          selected = false,
          comfortable = true,
          onClick = {
            categorySearch = ""
            showCategoryModal = true
          },
        )
        visibleCategories.forEach { category ->
          BrChip(
            category.name,
            selected = category.id in filters.categoryIds,
            comfortable = true,
            colorDot = parseCategoryColor(category.color),
            onClick = { onFiltersChange(filters.copy(categoryIds = toggle(filters.categoryIds, category.id))) },
          )
        }
      }

      if (showSortRow) ChipRow("SORT") {
        SortOrder.entries.forEach { so ->
          BrChip(so.label, selected = filters.sort == so, comfortable = true, onClick = { onFiltersChange(filters.copy(sort = so)) })
        }
      }
    }
  }
  Spacer(Modifier.height(8.dp))

  // Custom date RANGE picker.
  if (showRangePicker) {
    val rangeState = rememberDateRangePickerState()
    BrDialog(
      title = "Choose date range",
      onDismiss = { showRangePicker = false },
      confirmText = "Apply",
      confirmEnabled = rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null,
      onConfirm = {
        val from = rangeState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        val to = rangeState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
        if (from != null && to != null) onFiltersChange(filters.copy(dateFrom = from, dateTo = to))
        showRangePicker = false
      },
      wide = true,
    ) {
      DateRangePicker(state = rangeState, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp))
    }
  }

  // Category modal: an empty stored set is the canonical unfiltered state.
  // It intentionally renders with no checks; checks represent explicit picks.
  if (showCategoryModal) {
    BrDialog(
      title = "CATEGORIES",
      onDismiss = { showCategoryModal = false },
      confirmText = "DONE",
      onConfirm = { showCategoryModal = false },
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrTextField(categorySearch, { categorySearch = it }, "SEARCH CATEGORIES")
        val matchingCategories = visibleCategories.filter { it.name.contains(categorySearch, ignoreCase = true) }
        if (matchingCategories.isEmpty()) {
          Text("NO CATEGORIES MATCH", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        } else {
          LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(items = matchingCategories, key = { it.id }) { c ->
              val selected = c.id in filters.categoryIds
              Row(
                Modifier
                  .fillMaxWidth()
                  .background(if (selected) spec.surfaceAlt else spec.surface)
                  .clickable {
                    onFiltersChange(filters.copy(categoryIds = toggle(filters.categoryIds, c.id)))
                  }
                  .padding(horizontal = 10.dp, vertical = 9.dp),
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
        val duplicateNames = visibleAccounts.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        val matchingAccounts = visibleAccounts.filter { it.name.contains(accountSearch, ignoreCase = true) }
        if (matchingAccounts.isEmpty()) {
          Text("NO ACCOUNTS MATCH", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        } else {
          LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(items = matchingAccounts, key = { it.id }) { a ->
              val selected = a.id in filters.accountIds
              val suffix = if (a.name in duplicateNames) currencies.firstOrNull { it.id == a.currencyId }?.code else null
              Row(
                Modifier
                  .fillMaxWidth()
                  .background(if (selected) spec.surfaceAlt else spec.surface)
                  .clickable { onFiltersChange(filters.copy(accountIds = toggle(filters.accountIds, a.id))) }
                  .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f))
                if (suffix != null) {
                  Text(
                    "(${suffix.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.muted,
                    modifier = Modifier.alignByBaseline(),
                  )
                }
                Text(if (selected) "✓" else "", style = MaterialTheme.typography.labelLarge, color = spec.accent)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChipRow(label: String, detail: String? = null, content: @Composable () -> Unit) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      if (detail != null) {
        Text(
          detail,
          style = MaterialTheme.typography.labelSmall,
          color = spec.accent,
          maxLines = 1,
        )
      }
    }
    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      content()
    }
  }
}

private fun toggle(set: Set<String>, id: String): Set<String> = if (id in set) set - id else set + id

fun parseCategoryColor(hex: String): Color = parseHexColor(hex)
