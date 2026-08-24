package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrCard
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import kotlinx.coroutines.launch

/**
 * Currencies & Accounts: one section per currency listing its accounts
 * individually (with balances), plus add/edit/delete/restore actions and a
 * tap-through to each account's detail screen.
 */
@Composable
fun CurrenciesAccountsScreen(
  onBack: () -> Unit,
  onOpenAccount: (String) -> Unit,
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
  val archivedAccounts by remember(ownerId) { container.accountRepository.observeArchived(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var showAddCurrency by remember { mutableStateOf(false) }
  var editingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
  var deletingCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
  var addingAccountFor by remember { mutableStateOf<CurrencyEntity?>(null) }
  var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var info by remember { mutableStateOf<String?>(null) }

  Column(modifier.fillMaxSize().background(spec.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("< BACK", style = MaterialTheme.typography.labelMedium, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack))
    }
    ScreenHeader(title = "Currencies & Accounts", subtitle = "ONE CURRENCY, ITS ACCOUNTS INSIDE")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }
      info?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.accent) }

      BrButton(text = "+ Add currency", onClick = { showAddCurrency = true })

      // Extra breathing room between the action button and the first card.
      Spacer(Modifier.height(12.dp))

      if (currencies.isEmpty()) {
        Text("No currencies yet.", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
      }

      currencies.forEach { currency ->
        val currencyAccounts = accounts.filter { it.currencyId == currency.id }
        CurrencyCard(
          currency = currency,
          accounts = currencyAccounts,
          onSetDefault = {
            scope.launch {
              try {
                container.currencyRepository.update(ownerId, currency.id, currency.code, currency.symbol, currency.name, isDefault = true)
                info = "${currency.code} is now the default currency"
                error = null
              } catch (e: Exception) { error = e.message }
            }
          },
          onEdit = { editingCurrency = currency },
          onDelete = { deletingCurrency = currency },
          onOpenAccount = onOpenAccount,
          onAddAccount = { addingAccountFor = currency },
          onEditAccount = { editingAccount = it },
          onSetDefaultAccount = { account ->
            scope.launch {
              try {
                container.accountRepository.setDefault(ownerId, account.id)
                info = "${account.name} is now the default account for ${currency.code}"
                error = null
              } catch (e: Exception) { error = e.message }
            }
          },
          onArchiveAccount = { account ->
            scope.launch {
              try {
                container.accountRepository.archive(ownerId, account.id)
                error = null
              } catch (e: Exception) { error = e.message }
            }
          },
        )
        // Clear separation between currency cards.
        Spacer(Modifier.height(12.dp))
      }

      if (archivedAccounts.isNotEmpty()) {
        BrCard(bordered = true, contentPadding = PaddingValues(12.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ARCHIVED ACCOUNTS", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            archivedAccounts.forEach { account ->
              val currency = currencies.firstOrNull { it.id == account.currencyId }
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(account.name, style = MaterialTheme.typography.bodyMedium, color = spec.muted)
                  Text(currency?.code ?: "", style = MaterialTheme.typography.labelSmall, color = spec.muted)
                }
                Text(
                  "RESTORE",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.accent,
                  modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable { scope.launch { container.accountRepository.restore(ownerId, account.id) } },
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
    }
  }

  if (showAddCurrency) {
    CurrencyDialog(
      title = "ADD CURRENCY",
      initial = null,
      onDismiss = { showAddCurrency = false },
      onSave = { code, symbol, name, isDefault ->
        scope.launch {
          try {
            container.currencyRepository.add(ownerId, code, symbol, name, isDefault)
            showAddCurrency = false
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editingCurrency?.let { currency ->
    CurrencyDialog(
      title = "EDIT CURRENCY",
      initial = currency,
      onDismiss = { editingCurrency = null },
      onSave = { code, symbol, name, isDefault ->
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
    }
  }

  addingAccountFor?.let { currency ->
    AccountDialog(
      title = "ADD ACCOUNT",
      initialName = "",
      initialCurrencyId = currency.id,
      currencies = currencies,
      onDismiss = { addingAccountFor = null },
      onSave = { name, currencyId ->
        scope.launch {
          try {
            container.accountRepository.add(ownerId, currencyId, name)
            addingAccountFor = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editingAccount?.let { account ->
    AccountDialog(
      title = "EDIT ACCOUNT",
      initialName = account.name,
      initialCurrencyId = account.currencyId,
      currencies = currencies,
      onDismiss = { editingAccount = null },
      onSave = { name, currencyId ->
        scope.launch {
          try {
            container.accountRepository.update(ownerId, account.id, name, currencyId)
            editingAccount = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
      onDelete = {
        scope.launch {
          try {
            container.accountRepository.delete(ownerId, account.id)
            editingAccount = null
            error = null
            info = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }
}

@Composable
private fun CurrencyCard(
  currency: CurrencyEntity,
  accounts: List<AccountEntity>,
  onSetDefault: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onOpenAccount: (String) -> Unit,
  onAddAccount: () -> Unit,
  onEditAccount: (AccountEntity) -> Unit,
  onSetDefaultAccount: (AccountEntity) -> Unit,
  onArchiveAccount: (AccountEntity) -> Unit,
) {
  val spec = LocalThemeSpec.current
  BrCard(bordered = true, contentPadding = PaddingValues(12.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      // Currency header: code + symbol + default badge, actions on the right.
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currency.code, style = MaterialTheme.typography.labelLarge, color = spec.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(currency.symbol, style = MaterialTheme.typography.labelMedium, color = spec.muted)
            if (currency.isDefault) {
              Spacer(Modifier.width(8.dp))
              Text(
                "DEFAULT",
                style = MaterialTheme.typography.labelSmall,
                color = spec.onAccent,
                modifier = Modifier.background(spec.accent).padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Text(currency.name, style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!currency.isDefault) {
            Text("SET DEFAULT", style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onSetDefault))
          }
          Text("EDIT", style = MaterialTheme.typography.labelSmall, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onEdit))
          Text("DEL", style = MaterialTheme.typography.labelSmall, color = spec.expense, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onDelete))
        }
      }

      Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.45f)))

      // Accounts that belong to this currency.
      Text(
        "${accounts.size} ACCOUNT${if (accounts.size == 1) "" else "S"} IN ${currency.code}",
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
      )
      if (accounts.isEmpty()) {
        Text("No accounts yet — tap “+ Account”.", style = MaterialTheme.typography.bodySmall, color = spec.muted)
      } else {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          accounts.forEach { account ->
            AccountRow(
              account = account,
              onOpen = { onOpenAccount(account.id) },
              onEdit = { onEditAccount(account) },
              onSetDefault = { onSetDefaultAccount(account) },
              onArchive = { onArchiveAccount(account) },
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrChip(text = "+ Account", selected = false, onClick = onAddAccount)
      }
    }
  }
}

@Composable
private fun AccountRow(
  account: AccountEntity,
  onOpen: () -> Unit,
  onEdit: () -> Unit,
  onSetDefault: () -> Unit,
  onArchive: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier.weight(1f).minimumInteractiveComponentSize().clickable(onClick = onOpen).padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(6.dp).background(spec.accent))
      Spacer(Modifier.width(8.dp))
      Text(account.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink)
      if (account.isDefault) {
        Spacer(Modifier.width(6.dp))
        Text(
          "DEFAULT",
          style = MaterialTheme.typography.labelSmall,
          color = spec.onAccent,
          modifier = Modifier.background(spec.accent).padding(horizontal = 4.dp, vertical = 1.dp),
        )
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (!account.isDefault) {
        Text("SET DEFAULT", style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(2.dp).minimumInteractiveComponentSize().clickable(onClick = onSetDefault))
      }
      Text("EDIT", style = MaterialTheme.typography.labelSmall, color = spec.accent, modifier = Modifier.padding(2.dp).minimumInteractiveComponentSize().clickable(onClick = onEdit))
      Text("HIDE", style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(2.dp).minimumInteractiveComponentSize().clickable(onClick = onArchive))
    }
  }
}

@Composable
private fun CurrencyDialog(
  title: String,
  initial: CurrencyEntity?,
  onDismiss: () -> Unit,
  onSave: (code: String, symbol: String, name: String, isDefault: Boolean) -> Unit,
) {
  var code by remember { mutableStateOf(initial?.code ?: "") }
  var symbol by remember { mutableStateOf(initial?.symbol ?: "") }
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var isDefault by remember { mutableStateOf(initial?.isDefault ?: false) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    onConfirm = { onSave(code, symbol, name, isDefault) },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(code, { code = it }, "CODE (USD)")
      BrTextField(symbol, { symbol = it }, "SYMBOL ($)")
      BrTextField(name, { name = it }, "NAME")
      Row(verticalAlignment = Alignment.CenterVertically) {
        BrChip("Default currency", selected = isDefault, onClick = { isDefault = !isDefault })
      }
    }
  }
}

@Composable
private fun AccountDialog(
  title: String,
  initialName: String,
  initialCurrencyId: String,
  currencies: List<CurrencyEntity>,
  onDismiss: () -> Unit,
  onSave: (name: String, currencyId: String) -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  var name by remember { mutableStateOf(initialName) }
  var currencyId by remember { mutableStateOf(initialCurrencyId) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    onConfirm = { onSave(name, currencyId) },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(name, { name = it }, "ACCOUNT NAME (Cash)")
      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c ->
          BrChip(c.code, selected = currencyId == c.id, onClick = { currencyId = c.id })
        }
      }
      if (onDelete != null) {
        Text(
          "DELETE ACCOUNT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 4.dp).minimumInteractiveComponentSize().clickable(onClick = onDelete),
        )
      }
    }
  }
}
