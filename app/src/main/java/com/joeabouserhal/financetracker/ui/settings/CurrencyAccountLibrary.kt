package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*

internal enum class CurrencyAction { EDIT, DEFAULT, DELETE }
internal enum class AccountAction { EDIT, DEFAULT, ARCHIVE, RESTORE }

@Composable
internal fun CurrencyAccountLibrary(
  ownerId: String,
  currencies: List<CurrencyEntity>,
  accounts: List<AccountEntity>,
  archived: List<AccountEntity>,
  balances: Map<String, Long>,
  onBack: () -> Unit,
  onAddCurrency: () -> Unit,
  onAddAccount: (CurrencyEntity) -> Unit,
  onOpenAccount: (String) -> Unit,
  onCurrencyAction: (CurrencyEntity, CurrencyAction) -> Unit,
  onAccountAction: (AccountEntity, AccountAction) -> Unit,
  modifier: Modifier = Modifier,
  error: String? = null,
  info: String? = null,
) {
  val spec = LocalThemeSpec.current
  var search by rememberSaveable(ownerId) { mutableStateOf("") }
  var showArchived by rememberSaveable(ownerId) { mutableStateOf(false) }
  var currencyMenu by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
  var accountMenu by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
  val query = search.trim()
  val visible = currencies.sortedByDescending { it.isDefault }.filter { currency ->
    listOf(currency.code, currency.name).any { it.contains(query, true) } ||
      accounts.any { it.currencyId == currency.id && it.name.contains(query, true) }
  }

  ManagementPage(
    title = "Currencies & accounts",
    description = "Manage your currencies, balances, and accounts.",
    onBack = onBack, modifier = modifier,
    action = "New currency", onAdd = onAddCurrency,
    listTag = "currency-account-list", compact = true,
  ) {
      item("search") {
        Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          BrTextField(search, { search = it }, "FIND CURRENCY OR ACCOUNT", leadingIconRes = R.drawable.ic_search,
            trailingIconRes = if (search.isNotEmpty()) R.drawable.ic_close else null,
            trailingIconDescription = "Clear search", onTrailingIconClick = { search = "" })
          error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
          info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.accent) }
        }
      }
      if (visible.isEmpty()) item("empty") {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(if (currencies.isEmpty()) "Start with a currency" else "No matching currencies or accounts",
            style = MaterialTheme.typography.titleMedium, color = spec.ink)
          Text(if (currencies.isEmpty()) "Add a currency above to create its first account."
            else "Try a currency code, currency name, or active account name.",
            style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
      }
      visible.forEachIndexed { currencyIndex, currency ->
        val group = accounts.filter { it.currencyId == currency.id }.sortedByDescending { it.isDefault }
        if (currencyIndex > 0) item("currency-divider:${currency.id}") {
          Canvas(Modifier.fillMaxWidth().padding(vertical = 18.dp).height(1.dp).testTag("currency-divider:${currency.id}")) {
            drawLine(spec.border.copy(alpha = 0.5f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
              strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
          }
        }
        item("currency:${currency.id}") {
          Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                  itemVerticalAlignment = Alignment.CenterVertically) {
                  Text(currency.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = spec.ink)
                  if (currency.isDefault) Text("DEFAULT CURRENCY", style = MaterialTheme.typography.labelSmall, color = spec.accent)
                }
                Text(currency.name, style = MaterialTheme.typography.bodySmall, color = spec.muted,
                  maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              IconButton(onClick = { currencyMenu = currency.id }) {
                Icon(painterResource(R.drawable.ic_more), "Manage ${currency.code}", Modifier.size(22.dp), tint = spec.muted)
              }
            }
            com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(group.sumOf { balances[it.id] ?: 0L }, currency.symbol),
              style = MaterialTheme.typography.headlineSmall, color = spec.ink)
          }
        }
        itemsIndexed(group, key = { _, account -> "account:${account.id}" }) { index, account ->
          CurrencyAccountRow(
            account = account, balance = balances[account.id] ?: 0L, symbol = currency.symbol,
            divider = index > 0, onOpen = { onOpenAccount(account.id) }, onManage = { accountMenu = account.id },
          )
        }
        item("add-account:${currency.id}") {
          Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("account-footer:${currency.id}")) {
            if (group.isEmpty()) Text("No accounts in ${currency.code} yet.",
              Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = spec.muted)
            Row(
              Modifier.fillMaxWidth().clickable(role = Role.Button) { onAddAccount(currency) }
                .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = spec.accent, modifier = Modifier.size(16.dp))
              Text("ADD ${currency.code} ACCOUNT", style = MaterialTheme.typography.labelSmall, color = spec.accent)
            }
          }
        }
      }
      if (archived.isNotEmpty()) {
        item("archived-toggle") {
          Row(
            Modifier.fillMaxWidth().padding(top = 18.dp)
              .clickable(role = Role.Button) { showArchived = !showArchived }
              .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(if (showArchived) "HIDE ARCHIVED (${archived.size})" else "ARCHIVED ACCOUNTS (${archived.size})",
              style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = spec.muted,
              modifier = Modifier.size(18.dp).rotate(if (showArchived) 270f else 90f))
          }
        }
        if (showArchived) {
          itemsIndexed(archived, key = { _, account -> "archived:${account.id}" }) { index, account ->
            val currency = currencies.firstOrNull { it.id == account.currencyId }
            CurrencyAccountRow(account, balances[account.id] ?: 0L, currency?.symbol.orEmpty(),
              divider = index > 0, onOpen = { accountMenu = account.id }, onManage = { accountMenu = account.id },
              archivedCurrency = currency?.code.orEmpty())
          }
        }
      }
  }

  currencies.firstOrNull { it.id == currencyMenu }?.let { currency ->
    ManagementActions(currency.code, { currencyMenu = null }, buildList {
      add(ManagementAction("Edit currency", "Name, code, and symbol") { onCurrencyAction(currency, CurrencyAction.EDIT) })
      if (!currency.isDefault) add(ManagementAction("Make default", "Preselect ${currency.code} for new entries") { onCurrencyAction(currency, CurrencyAction.DEFAULT) })
      add(ManagementAction("Delete currency", "Only available when it has no transactions", true) { onCurrencyAction(currency, CurrencyAction.DELETE) })
    })
  }
  (accounts + archived).firstOrNull { it.id == accountMenu }?.let { account ->
    ManagementActions(account.name, { accountMenu = null }, buildList {
      if (account.archived) {
        add(ManagementAction("Restore account", "Return it to your active accounts") { onAccountAction(account, AccountAction.RESTORE) })
      } else {
        add(ManagementAction("Edit account", "Change its name or currency") { onAccountAction(account, AccountAction.EDIT) })
        if (!account.isDefault) add(ManagementAction("Make default", "Use it first for this currency") { onAccountAction(account, AccountAction.DEFAULT) })
        add(ManagementAction("Archive account", "Hide it from new entries; keep its history") { onAccountAction(account, AccountAction.ARCHIVE) })
      }
    })
  }
}

@Composable
private fun CurrencyAccountRow(
  account: AccountEntity, balance: Long, symbol: String,
  divider: Boolean, onOpen: () -> Unit, onManage: () -> Unit,
  archivedCurrency: String? = null,
) {
  val spec = LocalThemeSpec.current
  val largeText = LocalDensity.current.fontScale >= 1.3f
  Column(Modifier.fillMaxWidth().background(spec.surface)) {
    if (divider) Box(
      Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp)
        .background(spec.border.copy(alpha = 0.3f)).testTag("management-row-divider"),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen)
          .heightIn(min = 56.dp).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(account.name, style = MaterialTheme.typography.bodyLarge, color = spec.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (archivedCurrency != null) Text("$archivedCurrency · Archived",
            style = MaterialTheme.typography.labelSmall, color = spec.muted)
          else if (account.isDefault) Text("DEFAULT", style = MaterialTheme.typography.labelSmall, color = spec.accent)
          if (largeText) com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(balance, symbol), style = MaterialTheme.typography.labelLarge,
            color = if (balance < 0) spec.expense else spec.ink)
        }
        if (!largeText) com.joeabouserhal.financetracker.ui.components.PrivateAmount(compactCurrencyText(balance, symbol), style = MaterialTheme.typography.labelLarge,
          color = if (balance < 0) spec.expense else spec.ink)
      }
      IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_more), "Manage ${account.name}", Modifier.size(22.dp), tint = spec.muted)
      }
    }
  }
}
