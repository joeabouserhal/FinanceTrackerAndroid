package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.ui.components.ManagementListRow
import com.joeabouserhal.financetracker.ui.components.compactCurrencyText
import com.joeabouserhal.financetracker.utils.parseHexColor

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
  divider: Boolean = false,
) {
  val spec = LocalThemeSpec.current
  val currencyLabelSize = MaterialTheme.typography.labelSmall.fontSize
  val currency = currencies.firstOrNull { it.id == preset.defaultCurrencyId }
  val account = accounts.firstOrNull { it.id == preset.defaultAccountId }
  val category = categories.firstOrNull { it.id == preset.defaultCategoryId }
  val summary = listOfNotNull(category?.name, account?.name).joinToString(" · ")

  ManagementListRow(
    title = preset.name,
    description = summary.ifBlank { "Details chosen when you use it" },
    onClick = onTap, icon = R.drawable.ic_tab_presets, divider = divider,
    compact = true,
    tint = category?.let { parseHexColor(it.color) } ?: spec.muted,
    detail = {
      Text(
        buildAnnotatedString {
          preset.defaultAmount?.let { append(compactCurrencyText(it, currency?.symbol ?: "")) }
            ?: append("Flexible amount")
          if (currency != null) withStyle(SpanStyle(color = spec.muted, fontSize = currencyLabelSize)) {
            append(" · ${currency.code}")
          }
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (preset.defaultAmount == null) spec.muted else if (preset.type == TransactionType.EXPENSE) spec.expense else spec.income,
      )
    }
  )
}
