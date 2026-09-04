package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import com.joeabouserhal.financetracker.utils.parseHexColor
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

/** Presets page filter: one type or everything. */
enum class PresetFilter(val type: TransactionType?) {
  ALL(null),
  EXPENSE(TransactionType.EXPENSE),
  INCOME(TransactionType.INCOME),
}

@Composable
fun PresetsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filter by rememberSaveable(ownerId) { mutableStateOf(PresetFilter.ALL) }

  val filterType = filter.type
  val presets by remember(ownerId) {
    container.presetRepository.observeAll(ownerId)
  }.collectAsStateWithLifecycle(initialValue = emptyList())
  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var adding by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<PresetEntity?>(null) }
  var archiving by remember { mutableStateOf<PresetEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  PresetLibrary(
    ownerId, presets, currencies, accounts, categories, filter, { filter = it }, onBack,
    onSelect = { error = null; editing = it }, modifier = modifier, onAdd = { error = null; adding = true }, error = error,
  )

  if (adding) {
    PresetDialog(
      title = "ADD PRESET",
      initial = null,
      defaultType = filter.type ?: TransactionType.EXPENSE,
      saveError = error,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { adding = false },
      onSave = { name, amount, currencyId, accountId, categoryId, presetType ->
        scope.launch {
          try {
            container.presetRepository.add(ownerId, name, presetType, amount, currencyId, categoryId, accountId)
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
      defaultType = preset.type,
      saveError = error,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { editing = null },
      onSave = { name, amount, currencyId, accountId, categoryId, _ ->
        scope.launch {
          try {
            container.presetRepository.update(ownerId, preset.id, name, amount, currencyId, categoryId, accountId)
            editing = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
      onDelete = { editing = null; error = null; archiving = preset },
    )
  }

  archiving?.let { preset ->
    BrDialog(
      title = "DELETE PRESET?",
      onDismiss = { archiving = null },
      confirmText = "DELETE",
      onConfirm = {
        scope.launch {
          try {
            container.presetRepository.archive(ownerId, preset.id)
            archiving = null
            editing = null
            error = null
          } catch (e: Exception) { error = e.message ?: "Could not delete this preset." }
        }
      },
    ) {
      Text("It disappears from the quick-select row but keeps its history.", style = MaterialTheme.typography.bodyMedium)
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
    }
  }
}

@Composable
internal fun PresetDialog(
  title: String,
  initial: PresetEntity?,
  defaultType: TransactionType,
  currencies: List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>,
  accounts: List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity>,
  categories: List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>,
  onDismiss: () -> Unit,
  onSave: (name: String, amount: Long?, currencyId: String?, accountId: String?, categoryId: String?, type: TransactionType) -> Unit,
  onDelete: (() -> Unit)? = null,
  saveError: String? = null,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var amountText by remember { mutableStateOf(initial?.defaultAmount?.let { minor -> if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}" } ?: "") }
  var dialogType by remember { mutableStateOf(initial?.type ?: defaultType) }
  var currencyId by remember { mutableStateOf(initial?.defaultCurrencyId) }
  var accountId by remember { mutableStateOf(initial?.defaultAccountId) }
  var categoryId by remember { mutableStateOf(initial?.defaultCategoryId) }
  var dialogError by remember { mutableStateOf<String?>(null) }
  var showCategoryModal by remember { mutableStateOf(false) }
  var categorySearch by remember { mutableStateOf("") }
  val spec = LocalThemeSpec.current

  // "Other" is always first in the list, matching the transaction form.
  val orderedCategories =
    remember(categories, dialogType) {
      categories
        .filter { it.type == dialogType }
        .sortedWith(
          compareBy<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity> {
            if (it.isDefault && it.name == "Other") 0 else 1
          }.thenBy { it.name },
        )
    }

  // New presets start with the default currency and its default account
  // selected, and fall back to the "Other" category, so saving right away
  // already has valid defaults.
  LaunchedEffect(currencies, accounts, orderedCategories) {
    if (initial == null) {
      if (currencyId == null) {
        currencyId = currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id
      }
      if (accountId == null) {
        val forCurrency = accounts.filter { it.currencyId == currencyId }
        accountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
      }
      if (categoryId == null) {
        categoryId = orderedCategories.firstOrNull { it.name == "Other" }?.id ?: orderedCategories.firstOrNull()?.id
      }
    }
  }

  // Keep the selected category valid when the dialog's type changes.
  LaunchedEffect(orderedCategories) {
    if (categoryId == null || orderedCategories.none { it.id == categoryId }) {
      categoryId = orderedCategories.firstOrNull { it.name == "Other" }?.id ?: orderedCategories.firstOrNull()?.id
    }
  }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    scrollContent = true,
    onConfirm = {
      dialogError = null
      if (name.isBlank()) {
        dialogError = "Preset name is required"
      } else {
        val amount = amountText.trim().takeIf { it.isNotBlank() }?.let {
          try { BigDecimal(Money.normalizeDecimalInput(it)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
        if (amountText.isNotBlank() && (amount == null || amount <= 0)) {
          dialogError = "Amount must be a positive number"
        } else {
          onSave(name, amount, currencyId, accountId, categoryId, dialogType)
        }
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (initial == null) {
        BrSegmentedToggle(
          options = listOf("Expense", "Income"),
          selectedIndex = if (dialogType == TransactionType.EXPENSE) 0 else 1,
          onSelect = { dialogType = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
          optionColors = listOf(spec.expense, spec.income),
        )
      }
      BrTextField(name, { name = it }, "NAME")
      BrTextField(amountText, { amountText = it }, "AMOUNT (OPTIONAL)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      (dialogError ?: saveError)?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
      }
      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c ->
          BrChip(
            c.code,
            selected = currencyId == c.id,
            large = true,
            onClick = {
              currencyId = c.id
              val forCurrency = accounts.filter { it.currencyId == c.id }
              accountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
            },
          )
        }
      }
      Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.filter { it.currencyId == currencyId }.forEach { a -> BrChip(a.name, selected = accountId == a.id, large = true, onClick = { accountId = a.id }) }
      }
      Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        BrChip("ALL ▸", selected = false, onClick = { categorySearch = ""; showCategoryModal = true })
        orderedCategories.forEach { c -> BrChip(c.name, selected = categoryId == c.id, onClick = { categoryId = c.id }, colorDot = parsePresetColor(c.color)) }
      }
      if (initial != null && onDelete != null) {
        Text(
          "DELETE PRESET",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onDelete)
            .padding(vertical = 4.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }

  if (showCategoryModal) {
    BrDialog(
      title = "All categories",
      onDismiss = { showCategoryModal = false },
      dismissText = null,
      wide = true,
    ) {
      Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BrTextField(
          value = categorySearch,
          onValueChange = { categorySearch = it },
          label = "SEARCH CATEGORIES",
          modifier = Modifier.fillMaxWidth(),
        )
        val filtered = orderedCategories.filter { it.name.contains(categorySearch, ignoreCase = true) }
        if (filtered.isEmpty()) {
          Text("No categories match", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        } else {
          LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(filtered, key = { it.id }) { category ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .background(if (categoryId == category.id) spec.surfaceAlt else spec.surface)
                  .clickable {
                    categoryId = category.id
                    showCategoryModal = false
                  }
                  .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  Modifier
                    .size(14.dp)
                    .background(parsePresetColor(category.color)),
                )
                Text(
                  category.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (categoryId == category.id) spec.accent else spec.ink,
                  modifier = Modifier.padding(horizontal = 12.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun parsePresetColor(hex: String): androidx.compose.ui.graphics.Color = parseHexColor(hex)
