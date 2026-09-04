package com.joeabouserhal.financetracker.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*

@Composable
internal fun CategoryLibrary(
  categories: List<CategoryEntity>,
  search: String,
  onSearch: (String) -> Unit,
  filter: String,
  onFilter: (String) -> Unit,
  onBack: (() -> Unit)?,
  onAdd: () -> Unit,
  onEdit: (CategoryEntity) -> Unit,
  modifier: Modifier = Modifier,
  error: String? = null,
) {
  val spec = LocalThemeSpec.current
  val editable = categories.filterNot { it.isDefault && it.name == "Other" }
  val visible = editable.filter { (filter == "ALL" || it.type.name == filter) && it.name.contains(search.trim(), true) }
  ManagementPage("Categories", "Give every entry a place. Tap a category to change its name or color.", onBack,
    modifier, action = "New category", onAdd = onAdd, listTag = "category-list") {
    item("filters") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrTextField(search, onSearch, "SEARCH CATEGORIES", leadingIconRes = R.drawable.ic_search,
          trailingIconRes = if (search.isNotEmpty()) R.drawable.ic_close else null,
          trailingIconDescription = "Clear search", onTrailingIconClick = { onSearch("") })
        ManagementTypeFilter(filter, onFilter)
        error?.let { Text(it, color = spec.expense, style = MaterialTheme.typography.bodySmall) }
      }
    }
    if (visible.isEmpty()) item("empty") {
      ManagementEmptyState(if (editable.isEmpty()) "A place for every transaction" else "No matching categories",
        if (editable.isEmpty()) "Add your own categories. Uncategorized entries still go to Other automatically." else "Try a different name or switch the transaction type.")
    }
    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
      val group = visible.filter { it.type == type }
      if (group.isNotEmpty()) {
        item("group:$type") { ManagementSection(if (type == TransactionType.EXPENSE) "Expense categories" else "Income categories", group.size) }
        itemsIndexed(group, key = { _, category -> "category:${category.id}" }) { index, category ->
          ManagementListRow(category.name, if (type == TransactionType.EXPENSE) "Expense" else "Income",
            onClick = { onEdit(category) }, icon = R.drawable.ic_tab_categories, tint = parseColor(category.color), divider = index > 0)
        }
      }
    }
    item("fallback-note") {
      Text("Other stays available as the automatic fallback.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
  }
}
