package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ThousandsSeparatorTransformation
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
  transactionId: String?,
  presetId: String? = null,
  onBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
  var amountText by rememberSaveable { mutableStateOf("") }
  var selectedCurrencyId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  var date by rememberSaveable { mutableStateOf(Dates.todayIso()) }
  var title by rememberSaveable { mutableStateOf("") }
  var notes by rememberSaveable { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showCategoryModal by remember { mutableStateOf(false) }
  var categorySearch by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  // All categories are loaded once; the per-type view is derived locally so
  // a type switch never races the flow reload and can't clobber a selection
  // that a preset just made.
  val allCategories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  // "Other" is always first in the list.
  val orderedCategories = remember(allCategories, type) {
    allCategories
      .filter { it.type == type }
      .sortedWith(
        compareBy<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity> {
          if (it.isDefault && it.name == "Other") 0 else 1
        }.thenBy { it.name },
      )
  }

  val currencyOptions = currencies
  val accountOptions = accounts.filter { it.currencyId == selectedCurrencyId }

  // Load existing transaction for edit mode.
  LaunchedEffect(transactionId, ownerId) {
    val existing = transactionId?.let { container.appDatabase.transactionDao().getById(ownerId, it) }
    if (existing != null) {
      type = existing.type
      amountText = formatAmountForInput(existing.amount)
      selectedCurrencyId = existing.currencyId
      selectedAccountId = existing.accountId
      selectedCategoryId = existing.categoryId
      date = existing.date
      title = existing.title.orEmpty()
      notes = existing.notes.orEmpty()
    }
  }

  // Pre-fill the form from a picked preset (add-from-preset flow).
  LaunchedEffect(presetId, ownerId) {
    val preset = presetId?.let { container.appDatabase.presetDao().getById(ownerId, it) }
    if (preset != null) {
      type = preset.type
      preset.defaultAmount?.let { amountText = formatAmountForInput(it) }
      preset.defaultCurrencyId?.let { selectedCurrencyId = it }
      preset.defaultAccountId?.let { selectedAccountId = it }
      preset.defaultCategoryId?.let { selectedCategoryId = it }
      title = preset.name
    }
  }

  // React to the currencies list arriving: always keep a valid selection and
  // default to the default currency (USD by default) in add mode. Never
  // clobber a selection while the list is still loading (preset pre-fill).
  LaunchedEffect(currencies) {
    if (currencies.isNotEmpty() &&
      (selectedCurrencyId == null || currencies.none { it.id == selectedCurrencyId })
    ) {
      selectedCurrencyId = currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id
    }
  }

  // React to the accounts list arriving: keep a valid account for the
  // selected currency, preferring that currency's default account.
  LaunchedEffect(accounts, selectedCurrencyId) {
    if (accounts.isNotEmpty() &&
      (selectedAccountId == null || accounts.none { it.id == selectedAccountId && it.currencyId == selectedCurrencyId })
    ) {
      val forCurrency = accounts.filter { it.currencyId == selectedCurrencyId }
      selectedAccountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
    }
  }

  // Keep a category always selected: whenever the list for the current type
  // doesn't contain the selection (e.g. after switching Expense ↔ Income),
  // fall back to the first entry, which is the seeded "Other". Only acts on a
  // loaded list so a preset's category survives the loading gap.
  LaunchedEffect(type, orderedCategories) {
    when {
      selectedCategoryId == null ->
        selectedCategoryId = orderedCategories.firstOrNull()?.id
      orderedCategories.isNotEmpty() && orderedCategories.none { it.id == selectedCategoryId } ->
        selectedCategoryId = orderedCategories.firstOrNull()?.id
    }
  }

  fun save() {
    val amountMinor = try {
      BigDecimal(amountText.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (_: Exception) {
      error = "Enter a valid amount"
      return
    }
    if (amountMinor <= 0) { error = "Amount must be positive"; return }
    val currencyId = selectedCurrencyId ?: run { error = "Pick a currency"; return }
    val accountId = selectedAccountId ?: run { error = "Pick an account"; return }

    scope.launch {
      try {
        if (transactionId == null) {
          container.transactionRepository.add(
            ownerId = ownerId,
            type = type,
            amount = amountMinor,
            currencyId = currencyId,
            categoryId = selectedCategoryId,
            accountId = accountId,
            date = date,
            title = title,
            notes = notes,
            presetId = null,
          )
        } else {
          container.transactionRepository.update(
            ownerId = ownerId,
            id = transactionId,
            type = type,
            amount = amountMinor,
            currencyId = currencyId,
            categoryId = selectedCategoryId,
            accountId = accountId,
            date = date,
            title = title,
            notes = notes,
            presetId = null,
          )
        }
        onSaved()
      } catch (e: Exception) {
        error = e.message ?: "Could not save"
      }
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(spec.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .imePadding(),
  ) {
    // Fixed header + full-width type toggle
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          if (transactionId == null) "ADD TRANSACTION" else "EDIT TRANSACTION",
          style = MaterialTheme.typography.headlineMedium,
          color = spec.ink,
        )
        Text(
          "✕",
          style = MaterialTheme.typography.headlineMedium,
          color = spec.muted,
          modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .minimumInteractiveComponentSize()
            .clickable(onClick = onBack),
        )
      }

      BrSegmentedToggle(
        options = listOf("Expense", "Income"),
        selectedIndex = if (type == TransactionType.EXPENSE) 0 else 1,
        onSelect = { type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
        optionColors = listOf(spec.expense, spec.income),
      )
    }

    // Scrollable form body
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      BrTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = "AMOUNT",
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = remember { ThousandsSeparatorTransformation() },
        suffix = {
          currencies.firstOrNull { it.id == selectedCurrencyId }?.symbol?.let { symbol ->
            Text(symbol, style = MaterialTheme.typography.bodyLarge, color = spec.muted)
          }
        },
      )

      BrTextField(value = title, onValueChange = { title = it }, label = "TITLE (OPTIONAL)", modifier = Modifier.fillMaxWidth())

      SectionLabel("CURRENCY")
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencyOptions.forEach { currency ->
          BrChip(
            currency.code,
            selected = selectedCurrencyId == currency.id,
            onClick = {
              selectedCurrencyId = currency.id
              val forCurrency = accounts.filter { it.currencyId == currency.id }
              selectedAccountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
            },
          )
        }
      }

      SectionLabel("ACCOUNT")
      if (accountOptions.isEmpty()) {
        Text("No accounts for this currency", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          accountOptions.forEach { account ->
            BrChip(account.name, selected = selectedAccountId == account.id, onClick = { selectedAccountId = account.id })
          }
        }
      }

      SectionLabel("CATEGORY")
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        BrChip("ALL ▸", selected = false, onClick = { categorySearch = ""; showCategoryModal = true })
        orderedCategories.take(6).forEach { category ->
          BrChip(
            category.name,
            selected = selectedCategoryId == category.id,
            onClick = { selectedCategoryId = category.id },
            colorDot = parseCategoryColor(category.color),
          )
        }
        if (orderedCategories.size > 6) {
          BrChip("+${orderedCategories.size - 6}", selected = false, onClick = { categorySearch = ""; showCategoryModal = true })
        }
      }

      SectionLabel("DATE")
      Row(
        Modifier
          .fillMaxWidth()
          .background(spec.surface)
          .padding(12.dp)
          .clickable { showDatePicker = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(date, style = MaterialTheme.typography.bodyLarge, color = spec.ink)
        Text("CHANGE", style = MaterialTheme.typography.labelSmall, color = spec.accent)
      }

      BrTextField(value = notes, onValueChange = { notes = it }, label = "NOTES (OPTIONAL)", modifier = Modifier.fillMaxWidth(), singleLine = false)

      error?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense)
      }
    }

    // Pinned actions — the save button never scrolls out of view
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      BrButton(text = if (transactionId == null) "ADD TRANSACTION" else "UPDATE", onClick = { save() }, modifier = Modifier.fillMaxWidth())
      if (transactionId != null) {
        Text(
          "DELETE TRANSACTION",
          style = MaterialTheme.typography.labelMedium,
          color = spec.expense,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showDeleteConfirm = true }
            .padding(vertical = 8.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }

  if (showDeleteConfirm && transactionId != null) {
    com.joeabouserhal.financetracker.ui.components.BrDialog(
      title = "DELETE TRANSACTION?",
      onDismiss = { showDeleteConfirm = false },
      confirmText = "DELETE",
      onConfirm = {
        showDeleteConfirm = false
        scope.launch {
          container.transactionRepository.remove(ownerId, transactionId)
          onSaved()
        }
      },
    ) {
      Text("This removes it permanently.", style = MaterialTheme.typography.bodyMedium)
    }
  }

  if (showCategoryModal) {
    Dialog(onDismissRequest = { showCategoryModal = false }) {
      Column(
        Modifier
          .fillMaxWidth()
          .background(spec.surface)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("ALL CATEGORIES", style = MaterialTheme.typography.labelLarge, color = spec.ink)
          Text("✕", style = MaterialTheme.typography.labelLarge, color = spec.muted, modifier = Modifier.clickable { showCategoryModal = false }.padding(4.dp))
        }
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
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(filtered, key = { it.id }) { category ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedCategoryId = category.id
                    showCategoryModal = false
                  }
                  .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  Modifier
                    .size(14.dp)
                    .background(parseCategoryColor(category.color)),
                )
                Text(
                  category.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (selectedCategoryId == category.id) spec.accent else spec.ink,
                  modifier = Modifier.padding(horizontal = 12.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          datePickerState.selectedDateMillis?.let { millis ->
            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
          }
          showDatePicker = false
        }) { Text("OK") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("CANCEL") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
private fun SectionLabel(text: String) {
  val spec = LocalThemeSpec.current
  Text(text, style = MaterialTheme.typography.labelSmall, color = spec.muted)
}

private fun formatAmountForInput(minor: Long): String {
  val whole = minor / 100
  val fraction = (minor % 100).toInt()
  return if (fraction == 0) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}
