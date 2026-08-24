package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money

/**
 * "Add from preset" page: pick a preset with an All/Expense/Income filter.
 * Selecting one opens the add-transaction form pre-filled from it.
 */
@Composable
fun PresetPickerScreen(
  onBack: () -> Unit,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filter by rememberSaveable { mutableStateOf(PresetFilter.ALL) }
  val filterType = filter.type
  val presets by remember(ownerId, filter) {
    if (filterType == null) {
      container.presetRepository.observeAll(ownerId)
    } else {
      container.presetRepository.observeByType(ownerId, filterType)
    }
  }.collectAsStateWithLifecycle(initialValue = emptyList())
  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  Column(modifier.fillMaxSize().background(spec.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "< BACK",
        style = MaterialTheme.typography.labelMedium,
        color = spec.accent,
        modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack),
      )
    }
    ScreenHeader(title = "Add from preset", subtitle = "PICK A TEMPLATE — THE FORM OPENS PRE-FILLED")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrChip("All", selected = filter == PresetFilter.ALL, onClick = { filter = PresetFilter.ALL })
        BrChip("Expense", selected = filter == PresetFilter.EXPENSE, onClick = { filter = PresetFilter.EXPENSE }, colorDot = spec.expense)
        BrChip("Income", selected = filter == PresetFilter.INCOME, onClick = { filter = PresetFilter.INCOME }, colorDot = spec.income)
      }

      if (presets.isEmpty()) {
        Text("No presets here yet.", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
      }

      presets.forEach { preset ->
        val currency = currencies.firstOrNull { it.id == preset.defaultCurrencyId }
        val account = accounts.firstOrNull { it.id == preset.defaultAccountId }
        val category = categories.firstOrNull { it.id == preset.defaultCategoryId }
        val summary = listOfNotNull(category?.name, account?.name).joinToString(" · ")

        Row(
          Modifier
            .fillMaxWidth()
            .background(spec.surface)
            .clickable(onClick = { onPick(preset.id) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    }
  }
}
