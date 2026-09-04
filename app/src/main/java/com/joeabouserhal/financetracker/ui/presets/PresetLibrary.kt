package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*

@Composable
internal fun PresetLibrary(
  ownerId: String,
  presets: List<PresetEntity>,
  currencies: List<CurrencyEntity>,
  accounts: List<AccountEntity>,
  categories: List<CategoryEntity>,
  filter: PresetFilter,
  onFilter: (PresetFilter) -> Unit,
  onBack: () -> Unit,
  onSelect: (PresetEntity) -> Unit,
  modifier: Modifier = Modifier,
  onAdd: (() -> Unit)? = null,
  error: String? = null,
) {
  val spec = LocalThemeSpec.current
  var search by rememberSaveable(ownerId) { mutableStateOf("") }
  val visible = presets.filter { preset ->
    (filter.type == null || preset.type == filter.type) && preset.name.contains(search.trim(), ignoreCase = true)
  }
  ManagementPage(
    title = if (onAdd != null) "Presets" else "Add from preset",
    description = if (onAdd != null) "Repeat transactions, ready to reuse. Tap to edit." else "Pick a shortcut, then review before saving.",
    onBack = onBack, modifier = modifier, action = if (onAdd != null) "New preset" else null,
    onAdd = { onAdd?.invoke() }, listTag = "preset-list", compact = true,
  ) {
    item("filters") {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BrTextField(search, { search = it }, "SEARCH PRESETS", leadingIconRes = R.drawable.ic_search,
          trailingIconRes = if (search.isNotEmpty()) R.drawable.ic_close else null,
          trailingIconDescription = "Clear search", onTrailingIconClick = { search = "" })
        ManagementTypeFilter(filter.name) { onFilter(PresetFilter.valueOf(it)) }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
      }
    }
    if (visible.isEmpty()) item("empty") {
      ManagementEmptyState(if (presets.isEmpty()) "Make repeat entries easier" else "No matching presets",
        if (presets.isEmpty()) "Create a preset with a name and any details you use often. You can always change them before saving a transaction." else "Try a different name or switch the transaction type.")
    }
    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
      val group = visible.filter { it.type == type }
      if (group.isNotEmpty()) {
        item("group:$type") { ManagementSection(if (type == TransactionType.EXPENSE) "Expense presets" else "Income presets", group.size, compact = true) }
        itemsIndexed(group, key = { _, preset -> "preset:${preset.id}" }) { index, preset ->
          PresetRowView(preset, currencies, accounts, categories, onTap = { onSelect(preset) }, divider = index > 0)
        }
      }
    }
  }
}
