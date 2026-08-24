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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

@Composable
fun PresetsScreen() {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
  val presets by remember(ownerId, type) { container.presetRepository.observeByType(ownerId, type) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by remember(ownerId, type) { container.categoryRepository.observeByType(ownerId, type) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var adding by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<PresetEntity?>(null) }
  var archiving by remember { mutableStateOf<PresetEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  Column(Modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Presets", subtitle = "ONE-TAP TEMPLATES FOR THE ADD-TRANSACTION FORM")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrChip("Expense", selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, colorDot = spec.expense)
        BrChip("Income", selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, colorDot = spec.income)
      }

      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }

      presets.forEach { preset ->
        PresetRow(preset, currencies, accounts, categories, onEdit = { editing = preset }, onArchive = { archiving = preset })
      }

      BrButton(text = "+ Add preset", onClick = { adding = true })
    }
  }

  if (adding) {
    PresetDialog(
      title = "ADD PRESET",
      initial = null,
      type = type,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { adding = false },
      onSave = { name, amount, currencyId, accountId, categoryId ->
        scope.launch {
          try {
            container.presetRepository.add(ownerId, name, type, amount, currencyId, categoryId, accountId)
            adding = false
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editing?.let { preset ->
    PresetDialog(
      title = "EDIT PRESET",
      initial = preset,
      type = preset.type,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { editing = null },
      onSave = { name, amount, currencyId, accountId, categoryId ->
        scope.launch {
          try {
            container.presetRepository.update(ownerId, preset.id, name, amount, currencyId, categoryId, accountId)
            editing = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  archiving?.let { preset ->
    BrDialog(
      title = "ARCHIVE PRESET?",
      onDismiss = { archiving = null },
      confirmText = "ARCHIVE",
      onConfirm = {
        scope.launch { container.presetRepository.archive(ownerId, preset.id); archiving = null }
      },
    ) {
      Text("It disappears from the quick-select row but keeps its history.", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun PresetRow(
  preset: PresetEntity,
  currencies: List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>,
  accounts: List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity>,
  categories: List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>,
  onEdit: () -> Unit,
  onArchive: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val currency = currencies.firstOrNull { it.id == preset.defaultCurrencyId }
  val account = accounts.firstOrNull { it.id == preset.defaultAccountId }
  val category = categories.firstOrNull { it.id == preset.defaultCategoryId }
  val summary =
    listOfNotNull(
      category?.name,
      account?.name,
      preset.defaultAmount?.let { Money.format(it, currency?.symbol ?: "") },
    ).joinToString(" · ")

  Row(
    Modifier.fillMaxWidth().background(spec.surface).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink)
      if (summary.isNotBlank()) {
        Text(summary, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      }
    }
    Text("EDIT", style = MaterialTheme.typography.labelSmall, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onEdit))
    Text("HIDE", style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onArchive))
  }
}

@Composable
private fun PresetDialog(
  title: String,
  initial: PresetEntity?,
  type: TransactionType,
  currencies: List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>,
  accounts: List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity>,
  categories: List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>,
  onDismiss: () -> Unit,
  onSave: (name: String, amount: Long?, currencyId: String?, accountId: String?, categoryId: String?) -> Unit,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var amountText by remember { mutableStateOf(initial?.defaultAmount?.let { minor -> if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}" } ?: "") }
  var currencyId by remember { mutableStateOf(initial?.defaultCurrencyId) }
  var accountId by remember { mutableStateOf(initial?.defaultAccountId) }
  var categoryId by remember { mutableStateOf(initial?.defaultCategoryId) }
  var dialogError by remember { mutableStateOf<String?>(null) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    onConfirm = {
      if (name.isBlank()) {
        dialogError = "Preset name is required"
      } else {
        val amount = amountText.trim().takeIf { it.isNotBlank() }?.let {
          try { BigDecimal(it).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
        if (amountText.isNotBlank() && (amount == null || amount <= 0)) {
          dialogError = "Amount must be a positive number"
        } else {
          onSave(name, amount, currencyId, accountId, categoryId)
        }
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(name, { name = it }, "NAME")
      BrTextField(amountText, { amountText = it }, "AMOUNT (OPTIONAL)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      dialogError?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
      }
      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c -> BrChip(c.code, selected = currencyId == c.id, onClick = { currencyId = c.id; accountId = accounts.firstOrNull { it.currencyId == c.id }?.id }) }
      }
      Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.filter { it.currencyId == currencyId }.forEach { a -> BrChip(a.name, selected = accountId == a.id, onClick = { accountId = a.id }) }
      }
      Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { c -> BrChip(c.name, selected = categoryId == c.id, onClick = { categoryId = c.id }, colorDot = parsePresetColor(c.color)) }
      }
    }
  }
}

private fun parsePresetColor(hex: String): androidx.compose.ui.graphics.Color =
  try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) } catch (_: IllegalArgumentException) { androidx.compose.ui.graphics.Color(0xFF77746C) }
