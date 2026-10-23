package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.joeabouserhal.financetracker.utils.OpeningBalance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

/**
 * Currencies & Accounts: one section per currency listing its accounts
 * individually (with balances), plus add/edit/delete/restore actions and a
 * tap-through to each account's detail screen.
 */
@Composable
fun CurrenciesAccountsScreen(
  ownerId: String,
  onBack: () -> Unit,
  onOpenAccount: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val archivedAccounts by remember(ownerId) { container.accountRepository.observeArchived(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var showAddCurrency by remember { mutableStateOf(false) }
  var editingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
  var deletingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
  var addingAccountFor by remember { mutableStateOf<CurrencyEntity?>(null) }
  var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var info by remember { mutableStateOf<String?>(null) }
  var savingCurrency by remember(ownerId) { mutableStateOf(false) }
  var savingAccount by remember(ownerId) { mutableStateOf(false) }

  val transactions by remember(ownerId) { container.transactionRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val balances = remember(transactions, accounts, archivedAccounts, ownerId) {
    com.joeabouserhal.financetracker.data.repositories.accountBalanceMap(transactions.filter { it.ownerId == ownerId },
      (accounts + archivedAccounts).filter { it.ownerId == ownerId })
  }
  var archivingAccount by remember(ownerId) { mutableStateOf<AccountEntity?>(null) }
  var deletingAccount by remember(ownerId) { mutableStateOf<AccountEntity?>(null) }

  CurrencyAccountLibrary(
    ownerId, currencies.filter { it.ownerId == ownerId }, accounts.filter { it.ownerId == ownerId },
    archivedAccounts.filter { it.ownerId == ownerId }, balances,
    onBack, onAddCurrency = { error = null; showAddCurrency = true },
    onAddAccount = { error = null; addingAccountFor = it }, onOpenAccount = onOpenAccount,
    onCurrencyAction = { currency, action ->
      error = null
      info = null
      when (action) {
        CurrencyAction.EDIT -> editingCurrency = currency
        CurrencyAction.DELETE -> deletingCurrency = currency
        CurrencyAction.DEFAULT -> scope.launch {
          try {
            container.currencyRepository.update(ownerId, currency.id, currency.code, currency.symbol, currency.name, isDefault = true)
            info = "${currency.code} is now the default currency"
          } catch (e: Exception) { error = e.message ?: "Could not change the default currency." }
        }
      }
    },
    onAccountAction = { account, action ->
      error = null
      info = null
      when (action) {
        AccountAction.EDIT -> editingAccount = account
        AccountAction.ARCHIVE -> archivingAccount = account
        AccountAction.DEFAULT -> scope.launch {
          try {
            container.accountRepository.setDefault(ownerId, account.id)
            info = "${account.name} is now the default account"
          } catch (e: Exception) { error = e.message ?: "Could not change the default account." }
        }
        AccountAction.RESTORE -> scope.launch {
          try {
            container.accountRepository.restore(ownerId, account.id)
            info = "${account.name} restored"
          } catch (e: Exception) { error = e.message ?: "Could not restore this account." }
        }
      }
    }, modifier = modifier, error = error, info = info,
  )

  archivingAccount?.let { account ->
    BrDialog("ARCHIVE ACCOUNT?", { archivingAccount = null }, confirmText = "ARCHIVE", onConfirm = {
      scope.launch {
        try {
          container.accountRepository.archive(ownerId, account.id)
          archivingAccount = null
          error = null
        } catch (e: Exception) { error = e.message ?: "Could not archive this account." }
      }
    }) {
      Text("${account.name} will be hidden from new entries. Its history stays, and you can restore it here.")
      error?.let { Text(it, color = spec.expense) }
    }
  }
  deletingAccount?.let { account ->
    BrDialog("DELETE ACCOUNT?", { deletingAccount = null }, confirmText = "DELETE", onConfirm = {
      scope.launch {
        try {
          container.accountRepository.delete(ownerId, account.id)
          deletingAccount = null
          error = null
        } catch (e: Exception) { error = e.message ?: "Could not delete this account." }
      }
    }) {
      Text("Permanently delete ${account.name}? Accounts with transactions cannot be deleted; archive them instead.")
      error?.let { Text(it, color = spec.expense) }
    }
  }

  if (showAddCurrency) {
    CurrencyDialog(
      title = "ADD CURRENCY",
      initial = null,
      busy = savingCurrency,
      saveError = error,
      onDismiss = { showAddCurrency = false },
      onSave = { code, symbol, name, isDefault, openingBalance ->
        if (!savingCurrency) {
          savingCurrency = true
          error = null
          scope.launch {
            try {
              container.currencyRepository.add(ownerId, code, symbol, name, isDefault, openingBalance)
              showAddCurrency = false
              error = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { savingCurrency = false }
          }
        }
      },
    )
  }

  editingCurrency?.let { currency ->
    CurrencyDialog(
      title = "EDIT CURRENCY",
      initial = currency,
      saveError = error,
      onDismiss = { editingCurrency = null },
      onSave = { code, symbol, name, isDefault, _ ->
        scope.launch {
          try {
            container.currencyRepository.update(ownerId, currency.id, code, symbol, name, isDefault)
            editingCurrency = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  deletingCurrency?.let { currency ->
    BrDialog(
      title = "DELETE CURRENCY?",
      onDismiss = { deletingCurrency = null },
      confirmText = "DELETE",
      onConfirm = {
        scope.launch {
          try {
            val promoted = container.currencyRepository.delete(ownerId, currency.id)
            deletingCurrency = null
            error = null
            info = if (promoted != null) "$promoted is now the default currency" else null
          } catch (e: Exception) { error = e.message }
        }
      },
    ) {
      Text("Its accounts go with it. Transactions using this currency block deletion.", style = MaterialTheme.typography.bodyMedium)
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
    }
  }

  addingAccountFor?.let { currency ->
    AccountDialog(
      title = "ADD ACCOUNT",
      busy = savingAccount,
      initialName = "",
      saveError = error,
      initialCurrencyId = currency.id,
      currencies = currencies,
      onDismiss = { addingAccountFor = null },
      allowOpeningBalance = true,
      onSave = { name, currencyId, openingBalance ->
        if (!savingAccount) {
          savingAccount = true
          error = null
          scope.launch {
            try {
              container.accountRepository.add(ownerId, currencyId, name, openingBalance)
              addingAccountFor = null
              error = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { savingAccount = false }
          }
        }
      },
    )
  }

  editingAccount?.let { account ->
    AccountDialog(
      title = "EDIT ACCOUNT",
      initialName = account.name,
      saveError = error,
      initialCurrencyId = account.currencyId,
      currencies = currencies,
      onDismiss = { editingAccount = null },
      onSave = { name, currencyId, _ ->
        scope.launch {
          try {
            container.accountRepository.update(ownerId, account.id, name, currencyId)
            editingAccount = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
      onDelete = {
        editingAccount = null
        error = null
        deletingAccount = account
      },
    )
  }
}


@Composable
internal fun CurrencyDialog(
  title: String,
  initial: CurrencyEntity?,
  onDismiss: () -> Unit,
  onSave: (code: String, symbol: String, name: String, isDefault: Boolean, openingBalanceMinor: Long) -> Unit,
  saveError: String? = null,
  busy: Boolean = false,
) {
  var code by remember { mutableStateOf(initial?.code ?: "") }
  var symbol by remember { mutableStateOf(initial?.symbol ?: "") }
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var isDefault by remember { mutableStateOf(initial?.isDefault ?: false) }
  var openingBalance by remember { mutableStateOf("") }
  var validation by remember { mutableStateOf<String?>(null) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    scrollContent = true,
    confirmEnabled = !busy,
    onConfirm = {
      validation = when {
        code.isBlank() -> "Currency code is required"
        name.isBlank() -> "Currency name is required"
        else -> null
      }
      if (validation == null) {
        val amount = try { if (initial == null) OpeningBalance.parse(openingBalance) else 0L }
        catch (e: IllegalArgumentException) { validation = e.message; return@BrDialog }
        onSave(code, symbol, name, isDefault, amount)
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(name, { name = it }, "NAME · United States Dollar", enabled = !busy)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrTextField(symbol, { symbol = it }, "SYMBOL · $", modifier = Modifier.weight(1f), enabled = !busy)
        BrTextField(code, { code = it; validation = null }, "CODE · USD", modifier = Modifier.weight(1f), enabled = !busy)
      }
      if (initial == null) OpeningBalanceField(openingBalance, { openingBalance = it; validation = null }, "Starts in the default Cash account. Not counted as income.", enabled = !busy)
      (validation ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        BrChip("Default currency", selected = isDefault, onClick = { isDefault = !isDefault })
      }
    }
  }
}

@Composable
internal fun AccountDialog(
  title: String,
  initialName: String,
  initialCurrencyId: String,
  currencies: List<CurrencyEntity>,
  onDismiss: () -> Unit,
  onSave: (name: String, currencyId: String, openingBalanceMinor: Long) -> Unit,
  onDelete: (() -> Unit)? = null,
  saveError: String? = null,
  allowOpeningBalance: Boolean = false,
  busy: Boolean = false,
) {
  var name by remember { mutableStateOf(initialName) }
  var currencyId by remember { mutableStateOf(initialCurrencyId) }
  var openingBalance by remember { mutableStateOf("") }
  var validation by remember { mutableStateOf<String?>(null) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    scrollContent = true,
    confirmEnabled = !busy,
    onConfirm = {
      validation = when {
        name.isBlank() -> "Account name is required"
        currencies.none { it.id == currencyId } -> "Choose an available currency"
        else -> null
      }
      if (validation == null) {
        val amount = try { if (allowOpeningBalance) OpeningBalance.parse(openingBalance) else 0L }
        catch (e: IllegalArgumentException) { validation = e.message; return@BrDialog }
        onSave(name, currencyId, amount)
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(name, { name = it; validation = null }, "ACCOUNT NAME", enabled = !busy)
      (validation ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c ->
          BrChip(c.code, selected = currencyId == c.id, onClick = { currencyId = c.id })
        }
      }
      if (allowOpeningBalance) OpeningBalanceField(openingBalance, { openingBalance = it; validation = null },
        "Starting funds for this account. Not counted as income.", enabled = !busy)
      if (onDelete != null) {
        BrButton("Delete account", onDelete, style = com.joeabouserhal.financetracker.ui.components.BrButtonStyle.DANGER, compact = true)
      }
    }
  }
}

@Composable
private fun OpeningBalanceField(value: String, onValueChange: (String) -> Unit, description: String, enabled: Boolean = true) {
  BrTextField(value, onValueChange, "OPENING BALANCE (OPTIONAL)", modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled)
  Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
