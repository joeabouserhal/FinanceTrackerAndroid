package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.utils.Money

/**
 * Shared preset row: name + category · account on the left, the preset amount
 * (with its currency symbol, colored by type) on the right. Used by both the
 * Presets page and the "Add from preset" picker.
 */
@Composable
fun PresetRowView(
  preset: PresetEntity,
  currencies: List<CurrencyEntity>,
  accounts: List<AccountEntity>,
  categories: List<CategoryEntity>,
  onTap: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val currency = currencies.firstOrNull { it.id == preset.defaultCurrencyId }
  val account = accounts.firstOrNull { it.id == preset.defaultAccountId }
  val category = categories.firstOrNull { it.id == preset.defaultCategoryId }
  val summary = listOfNotNull(category?.name, account?.name).joinToString(" · ")

  Row(
    Modifier.fillMaxWidth().background(spec.surface).clickable(onClick = onTap).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink)
      if (summary.isNotBlank()) {
        Text(summary, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      }
    }
    preset.defaultAmount?.let { amount ->
      Text(
        Money.format(amount, currency?.symbol ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        color = if (preset.type == TransactionType.EXPENSE) spec.expense else spec.income,
      )
    }
  }
}
