package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ThousandsSeparatorTransformation
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.AmountExpression
import com.joeabouserhal.financetracker.utils.AmountExpressionResult
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
  ownerId: String,
  transactionId: String?,
  presetId: String? = null,
  onBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
  transferMode: Boolean = false,
  duplicateId: String? = null,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  // Navigation supplies the resolved active partition. A temporary guest fallback
  // here can falsely mark an account transaction missing before its data loads.
  val formScroll = rememberScrollState()
  var initialized by rememberSaveable(ownerId, transactionId, presetId, duplicateId) { mutableStateOf(false) }
  var saveNotice by remember { mutableStateOf<String?>(null) }

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accountNets by remember(ownerId) { container.appDatabase.transactionDao().observeAccountNets(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  var originalTransaction by remember(ownerId, transactionId) { mutableStateOf<TransactionEntity?>(null) }

  var type by rememberSaveable { mutableStateOf(if (transferMode) TransactionType.TRANSFER else TransactionType.EXPENSE) }
  var amountText by rememberSaveable { mutableStateOf("") }
  var selectedCurrencyId by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
  var destinationAccountId by rememberSaveable { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
  var date by rememberSaveable { mutableStateOf(Dates.todayIso()) }
  var title by rememberSaveable { mutableStateOf("") }
  var notes by rememberSaveable { mutableStateOf("") }
  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showCategoryModal by remember { mutableStateOf(false) }
  var categorySearch by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var amountFocused by remember { mutableStateOf(false) }
  var amountCursorRevision by remember { mutableStateOf(0) }
  val amountCalculation = remember(amountText) { AmountExpression.evaluate(amountText) }
  val hasPositivePlainAmount = remember(amountText, amountCalculation) {
    amountCalculation is AmountExpressionResult.NotExpression &&
      runCatching {
        BigDecimal(Money.normalizeDecimalInput(amountText))
          .setScale(2, RoundingMode.HALF_UP)
          .movePointRight(2)
          .longValueExact() > 0L
      }.getOrDefault(false)
  }
  val keyboardVisible = WindowInsets.isImeVisible
  val showAmountOperators = amountFocused && keyboardVisible

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
  val isTransfer = type == TransactionType.TRANSFER
  val transferCurrencies = remember(accounts) { transferCurrencyIds(accounts) }
  val accountOptions = accounts.filter { it.currencyId == selectedCurrencyId && (!it.archived ||
    transactionId != null && (it.id == selectedAccountId || it.id == destinationAccountId)) }

  // Load existing transaction for edit mode.
  LaunchedEffect(transactionId, duplicateId, ownerId) {
    val existing = (transactionId ?: duplicateId)?.let { container.appDatabase.transactionDao().getById(ownerId, it) }
    if (transactionId != null) originalTransaction = existing?.takeIf { it.deletedAt == null }
    if (initialized) return@LaunchedEffect
    if (existing != null && existing.deletedAt == null) {
      val draft = duplicateId?.let { TransactionDraft.duplicate(existing, Dates.todayIso()) }
      type = draft?.type ?: existing.type
      amountText = formatAmountForInput(existing.amount)
      selectedCurrencyId = existing.currencyId
      selectedAccountId = existing.accountId
      destinationAccountId = existing.destinationAccountId
      selectedCategoryId = existing.categoryId
      date = draft?.date ?: existing.date
      title = existing.title.orEmpty()
      notes = existing.notes.orEmpty()
      error = null
      initialized = true
    } else if (transactionId != null || duplicateId != null) {
      error = "The original transaction is no longer available."
    }
  }

  // Pre-fill the form from a picked preset (add-from-preset flow).
  LaunchedEffect(presetId, ownerId) {
    if (initialized) return@LaunchedEffect
    val preset = presetId?.let { container.appDatabase.presetDao().getById(ownerId, it) }
    if (preset != null) {
      type = preset.type
      preset.defaultAmount?.let { amountText = formatAmountForInput(it) }
      preset.defaultCurrencyId?.let { selectedCurrencyId = it }
      preset.defaultAccountId?.let { selectedAccountId = it }
      preset.defaultCategoryId?.let { selectedCategoryId = it }
      title = preset.name
      initialized = true
    }
  }

  // React to the currencies list arriving: always keep a valid selection and
  // default to the default currency (USD by default) in add mode. Never
  // clobber a selection while the list is still loading (preset pre-fill).
  LaunchedEffect(currencies, transferCurrencies, isTransfer) {
    // Prefer a usable transfer currency, but never replace a historical edit's currency.
    val available = if (isTransfer && transactionId == null) {
      currencies.filter { it.id in transferCurrencies }.ifEmpty { currencies }
    } else currencies
    if (available.isNotEmpty() &&
      (selectedCurrencyId == null || available.none { it.id == selectedCurrencyId })
    ) {
      selectedCurrencyId = available.firstOrNull { it.isDefault }?.id ?: available.firstOrNull()?.id
    }
  }

  // React to the accounts list arriving: keep a valid account for the
  // selected currency, preferring that currency's default account.
  LaunchedEffect(accounts, selectedCurrencyId) {
    if (accountOptions.isNotEmpty() &&
      (selectedAccountId == null || accountOptions.none { it.id == selectedAccountId })
    ) {
      val forCurrency = accountOptions
      selectedAccountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
    }
  }

  LaunchedEffect(accounts, selectedCurrencyId, selectedAccountId) {
    if (isTransfer && accountOptions.none { it.id == destinationAccountId && it.id != selectedAccountId }) {
      destinationAccountId = accountOptions.firstOrNull { it.id != selectedAccountId }?.id
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

  fun save(addAnother: Boolean = false) {
    if (saving) return
    when (val calculation = amountCalculation) {
      is AmountExpressionResult.Valid -> {
        error = "Tap OK to use the calculated amount"
        return
      }
      is AmountExpressionResult.Invalid -> {
        error = calculation.message
        return
      }
      AmountExpressionResult.NotExpression -> Unit
    }
    val amountMinor = try {
      BigDecimal(Money.normalizeDecimalInput(amountText)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (_: Exception) {
      error = "Enter a valid amount"
      return
    }
    if (amountMinor <= 0) { error = "Amount must be positive"; return }
    val currencyId = selectedCurrencyId ?: run { error = "Pick a currency"; return }
    val accountId = selectedAccountId ?: run { error = "Pick an account"; return }
    val destination = if (isTransfer) destinationAccountId ?: run { error = "Choose a destination account"; return } else null

    saving = true
    scope.launch {
      try {
        if (isTransfer) {
          container.transactionRepository.saveTransfer(
            ownerId = ownerId, id = transactionId, amount = amountMinor, currencyId = currencyId,
            sourceAccountId = accountId, destinationAccountId = requireNotNull(destination),
            date = date, title = title, notes = notes,
          )
        } else if (transactionId == null) {
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
        if (addAnother && transactionId == null) {
          amountText = ""
          title = ""
          notes = ""
          error = null
          saveNotice = "Saved. Ready for the next entry."
          formScroll.animateScrollTo(0)
        } else onSaved()
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        error = e.message ?: "Could not save"
      } finally {
        saving = false
      }
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(spec.background)
      // The window already resizes for the keyboard (adjustResize), so only
      // pad the system bars here. Padding by safeDrawing/ime would add the
      // keyboard height a second time and leave a huge gap above the IME.
      .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
      .navigationBarsPadding(),
  ) {
    // Fixed editorial header + the existing type toggle.
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            if (transactionId == null) "NEW ENTRY" else "EDIT ENTRY",
            style = MaterialTheme.typography.labelSmall,
            color = spec.accent,
          )
          Text(
            if (isTransfer) { if (transactionId == null) "TRANSFER FUNDS" else "EDIT TRANSFER" }
            else if (transactionId == null) "ADD TRANSACTION" else "EDIT TRANSACTION",
            style = MaterialTheme.typography.headlineSmall,
            color = spec.ink,
          )
        }
        Box(
          Modifier
            .size(42.dp)
            .background(spec.surface)
            .border(1.dp, spec.border)
            .clickable(onClick = onBack),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = "Close",
            tint = spec.muted,
            modifier = Modifier.size(21.dp),
          )
        }
      }

      if (isTransfer) {
        Text("Move money between accounts in the same currency.", style = MaterialTheme.typography.bodySmall, color = spec.muted)
      } else BrSegmentedToggle(
        options = listOf("Expense", "Income"),
        // GOAL withdrawals are money-out, so they sit on the Expense side.
        selectedIndex = if (type == TransactionType.INCOME) 1 else 0,
        onSelect = { type = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
        optionColors = listOf(spec.expense, spec.income),
      )
    }

    // Scrollable form body
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(formScroll)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      saveNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.muted) }
      val selectedCurrency = currencies.firstOrNull { it.id == selectedCurrencyId }
      AmountEditor(
        value = amountText,
        onValueChange = { amountText = it; error = null },
        cursorRevision = amountCursorRevision,
        onFocusChanged = { amountFocused = it },
        calculation = amountCalculation,
        onAcceptCalculation = { result ->
          amountText = AmountExpression.format(result)
          error = null
        },
        currencyCode = selectedCurrency?.code ?: "—",
        currencySymbol = selectedCurrency?.symbol ?: "",
        type = type,
      )

      if (!isTransfer) BrTextField(
        value = title,
        onValueChange = { title = it },
        label = "WHAT WAS IT FOR? (OPTIONAL)",
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("CURRENCY")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          currencyOptions.forEach { currency ->
            BrChip(
              currency.code,
              selected = selectedCurrencyId == currency.id,
              large = true,
              enabled = !isTransfer || currency.id in transferCurrencies,
              onClick = {
                selectedCurrencyId = currency.id
                val forCurrency = accounts.filter { it.currencyId == currency.id && !it.archived }
                selectedAccountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
              },
            )
          }
        }
      }

      if (!isTransfer) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel("ACCOUNT")
        if (accountOptions.isEmpty()) {
          Text("No accounts for this currency", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        } else {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            accountOptions.forEach { account ->
              BrChip(account.name, selected = selectedAccountId == account.id, large = true, onClick = { selectedAccountId = account.id })
            }
          }
        }
      }

      if (isTransfer) {
        TransferAccountFlow(
          accounts = accountOptions, sourceId = selectedAccountId, destinationId = destinationAccountId,
          currencyCode = selectedCurrency?.code ?: "—",
          onSourceChange = { selectedAccountId = it },
          onDestinationChange = { destinationAccountId = it },
          onSwap = {
            val source = selectedAccountId
            selectedAccountId = destinationAccountId
            destinationAccountId = source
          },
        )
        val preview = remember(accountNets, originalTransaction, selectedAccountId, destinationAccountId, amountText) {
          runCatching {
            transferBalanceChanges(accountNets.associate { it.accountId.orEmpty() to it.balanceMinor },
              originalTransaction, requireNotNull(selectedAccountId), requireNotNull(destinationAccountId),
              BigDecimal(Money.normalizeDecimalInput(amountText)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())
          }.getOrNull()
        }
        if (hasPositivePlainAmount && preview != null) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(selectedAccountId to preview.first, destinationAccountId to preview.second).forEach { (id, change) ->
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(accountOptions.firstOrNull { it.id == id }?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, color = spec.muted)
                com.joeabouserhal.financetracker.ui.components.PrivateAmount(
                  "${Money.format(change.before, selectedCurrency?.symbol.orEmpty())} → ${Money.format(change.after, selectedCurrency?.symbol.orEmpty())}",
                  style = MaterialTheme.typography.bodySmall, color = spec.ink)
              }
            }
          }
        }
        BrTextField(value = title, onValueChange = { title = it }, label = "TITLE (OPTIONAL)", modifier = Modifier.fillMaxWidth())
      } else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
      }

      SectionLabel("DATE")
      Row(
        Modifier
          .fillMaxWidth()
          .background(spec.surface)
          .border(1.dp, spec.border.copy(alpha = 0.55f))
          .clickable { showDatePicker = true }
          .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = spec.accent,
            modifier = Modifier.size(20.dp),
          )
          Text(date, style = MaterialTheme.typography.bodyLarge, color = spec.ink)
        }
        Text("CHANGE", style = MaterialTheme.typography.labelSmall, color = spec.accent)
      }

      BrTextField(value = notes, onValueChange = { notes = it }, label = "NOTES (OPTIONAL)", modifier = Modifier.fillMaxWidth(), singleLine = false)

      error?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense)
      }

      Spacer(Modifier.height(18.dp))
    }

    // The keyboard replaces the normal action dock with amount operators.
    // Closing it always restores the ordinary Add/Update action.
    if (!keyboardVisible || showAmountOperators) {
      Column(
        Modifier
          .fillMaxWidth()
          .background(spec.surface)
          .then(if (keyboardVisible) Modifier.imePadding() else Modifier),
      ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.55f)))
        Column(
          Modifier.padding(horizontal = 16.dp, vertical = if (showAmountOperators) 8.dp else 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (showAmountOperators) {
            AmountOperatorStrip(
              onAppend = { token ->
                amountText += token
                amountCursorRevision += 1
                error = null
              },
            )
          } else {
            BrButton(
              text = if (isTransfer) { if (transactionId == null) "TRANSFER" else "UPDATE TRANSFER" }
              else if (transactionId == null) "ADD TRANSACTION" else "UPDATE TRANSACTION",
              onClick = { save() },
              enabled = hasPositivePlainAmount && !saving && (!isTransfer || destinationAccountId != null && destinationAccountId != selectedAccountId),
              modifier = Modifier.fillMaxWidth(),
            )
            if (transactionId == null) BrButton(
              text = "SAVE & ADD ANOTHER",
              onClick = { save(addAnother = true) },
              enabled = hasPositivePlainAmount && !saving && (!isTransfer || destinationAccountId != null && destinationAccountId != selectedAccountId),
              style = com.joeabouserhal.financetracker.ui.components.BrButtonStyle.OUTLINE,
              compact = true,
              modifier = Modifier.fillMaxWidth(),
            )
            if (transactionId != null) {
              Text(
                if (isTransfer) "DELETE TRANSFER" else "DELETE TRANSACTION",
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
      }
    }
  }

  if (showDeleteConfirm && transactionId != null) {
    BrDialog(
      title = if (isTransfer) "DELETE TRANSFER?" else "DELETE TRANSACTION?",
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
      Text(
        if (type == TransactionType.GOAL)
          "This also un-completes its goal and removes the goal's other withdrawals."
        else if (isTransfer) "This removes the transfer and reverses its effect on both account balances."
        else "This removes it permanently.",
        style = MaterialTheme.typography.bodyMedium,
      )
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
          val listState = rememberLazyListState()
          Box(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            LazyColumn(
              Modifier.fillMaxWidth(),
              state = listState,
              contentPadding = PaddingValues(vertical = 5.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(filtered, key = { it.id }) { category ->
                Row(
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      selectedCategoryId = category.id
                      showCategoryModal = false
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
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
            // Thin scroll indicator on the right edge — only when scrollable.
            val info = listState.layoutInfo
            val scrollable = listState.canScrollForward || listState.canScrollBackward
            if (scrollable && info.totalItemsCount > 0 && info.visibleItemsInfo.isNotEmpty()) {
              Canvas(
                Modifier
                  .align(Alignment.CenterEnd)
                  .fillMaxHeight()
                  .width(4.dp),
              ) {
                val first = info.visibleItemsInfo.first()
                val last = info.visibleItemsInfo.last()
                val topFraction = first.index.toFloat() / info.totalItemsCount
                val bottomFraction = (last.index + 1).toFloat() / info.totalItemsCount
                val thumbFraction = (bottomFraction - topFraction).coerceAtLeast(0.06f)
                drawRoundRect(
                  color = spec.border.copy(alpha = 0.8f),
                  topLeft = Offset(0f, topFraction * size.height),
                  size = Size(size.width, thumbFraction * size.height),
                  cornerRadius = CornerRadius(2.dp.toPx()),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showDatePicker) {
    com.joeabouserhal.financetracker.ui.components.BrDatePickerDialog(
      title = "Choose date",
      onDismiss = { showDatePicker = false },
      initialDate = LocalDate.parse(date),
      onSelect = { chosen ->
        date = chosen.toString()
        showDatePicker = false
      },
    )
  }
}

@Composable
private fun AmountEditor(
  value: String,
  onValueChange: (String) -> Unit,
  cursorRevision: Int,
  onFocusChanged: (Boolean) -> Unit,
  calculation: AmountExpressionResult,
  onAcceptCalculation: (BigDecimal) -> Unit,
  currencyCode: String,
  currencySymbol: String,
  type: TransactionType,
) {
  val spec = LocalThemeSpec.current
  val typeColor = if (type == TransactionType.TRANSFER) spec.transfer else if (type == TransactionType.INCOME) spec.income else spec.expense
  val typeLabel = if (type == TransactionType.TRANSFER) "TRANSFER" else if (type == TransactionType.INCOME) "MONEY IN" else "MONEY OUT"
  val amountTransformation = remember { ThousandsSeparatorTransformation() }
  val focusRequester = remember { FocusRequester() }
  var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

  LaunchedEffect(value) {
    if (fieldValue.text != value) {
      fieldValue = TextFieldValue(value, TextRange(value.length))
    }
  }
  LaunchedEffect(cursorRevision) {
    if (cursorRevision > 0) {
      fieldValue = TextFieldValue(value, TextRange(value.length))
      focusRequester.requestFocus()
    }
  }

  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(
        "$typeLabel  ·  $currencyCode",
        style = MaterialTheme.typography.labelSmall,
        color = typeColor,
        modifier = Modifier.background(typeColor.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (currencySymbol.isNotBlank()) {
        Text(
          currencySymbol,
          style = MaterialTheme.typography.titleLarge,
          color = spec.muted,
          modifier = Modifier.padding(end = 8.dp),
        )
      }
      BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
          fieldValue = updated
          onValueChange(updated.text)
        },
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester)
          .onFocusChanged { onFocusChanged(it.isFocused) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = amountTransformation,
        textStyle = MaterialTheme.typography.headlineLarge.copy(color = spec.ink),
        cursorBrush = SolidColor(spec.accent),
        decorationBox = { innerTextField ->
          Box {
            if (fieldValue.text.isBlank()) {
              Text("0.00", style = MaterialTheme.typography.headlineLarge, color = spec.muted.copy(alpha = 0.42f))
            }
            innerTextField()
          }
        },
      )
    }
    when (calculation) {
      is AmountExpressionResult.Valid -> {
        Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            "= ${AmountExpression.format(calculation.value)}",
            style = MaterialTheme.typography.titleMedium,
            color = spec.ink.copy(alpha = 0.72f),
            modifier = Modifier.weight(1f),
          )
          BrButton("OK", onClick = { onAcceptCalculation(calculation.value) }, compact = true, fillWidth = false)
        }
        Text("Apply the result before saving this transaction.", style = MaterialTheme.typography.bodySmall, color = spec.muted)
      }
      is AmountExpressionResult.Invalid ->
        Text(calculation.message, style = MaterialTheme.typography.bodySmall, color = spec.expense)
      AmountExpressionResult.NotExpression -> Unit
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(typeColor.copy(alpha = 0.72f)))
  }
}

@Composable
private fun AmountOperatorStrip(onAppend: (String) -> Unit) {
  val spec = LocalThemeSpec.current
  Row(
    Modifier
      .fillMaxWidth()
      .height(42.dp)
      .background(spec.surface)
      .border(spec.borderWidth, spec.border),
  ) {
    listOf(
      "+" to "+",
      "−" to "-",
      "×" to "×",
      "÷" to "/",
    ).forEachIndexed { index, (label, token) ->
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clickable { onAppend(token) },
        contentAlignment = Alignment.Center,
      ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = spec.ink)
      }
      if (index < 3) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(spec.border))
      }
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
