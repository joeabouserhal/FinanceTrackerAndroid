package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
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
  val detailSize = MaterialTheme.typography.labelSmall.fontSize
  val detailWeight = MaterialTheme.typography.labelSmall.fontWeight
  var search by rememberSaveable(ownerId) { mutableStateOf("") }
  var showArchived by rememberSaveable(ownerId) { mutableStateOf(false) }
  var currencyMenu by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
  var accountMenu by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
  val query = search.trim()
  val visible = currencies.sortedByDescending { it.isDefault }.filter { currency ->
    listOf(currency.code, currency.name).any { it.contains(query, true) } ||
      accounts.any { it.currencyId == currency.id && it.name.contains(query, true) }
  }
  ManagementPage("Currencies & accounts", "Your currencies, accounts, and defaults.", onBack,
    modifier, action = "New currency", onAdd = onAddCurrency, listTag = "currency-account-list", compact = true) {
    item("search") {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${currencies.size} currencies · ${accounts.size} active accounts", style = MaterialTheme.typography.bodySmall, color = spec.muted)
        BrTextField(search, { search = it }, "FIND CURRENCY OR ACCOUNT", leadingIconRes = R.drawable.ic_search,
          trailingIconRes = if (search.isNotEmpty()) R.drawable.ic_close else null,
          trailingIconDescription = "Clear search", onTrailingIconClick = { search = "" })
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
        info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.accent) }
        Spacer(Modifier.height(6.dp))
      }
    }
    if (visible.isEmpty()) item("empty") {
      ManagementEmptyState(if (currencies.isEmpty()) "Start with a currency" else "No matching currencies or accounts",
        if (currencies.isEmpty()) "Add the currencies you use, then create accounts for cash, banks, or wallets." else "Try a currency code, currency name, or active account name.")
    }
    visible.forEachIndexed { currencyIndex, currency ->
      val group = accounts.filter { it.currencyId == currency.id }.sortedByDescending { it.isDefault }
      if (currencyIndex > 0) item("currency-divider:${currency.id}") {
        Canvas(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).testTag("currency-divider:${currency.id}")) {
          drawLine(spec.border.copy(alpha = 0.65f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
      }
      item("currency:${currency.id}") {
        Column(Modifier.fillMaxWidth().background(spec.surfaceAlt).border(1.dp, spec.border).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(currency.code, style = MaterialTheme.typography.titleLarge, color = spec.ink)
              Text(currency.name, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { currencyMenu = currency.id }) {
              Icon(painterResource(R.drawable.ic_more), "Manage ${currency.code}", Modifier.size(22.dp), tint = spec.muted)
            }
          }
          Text(compactCurrencyText(group.sumOf { balances[it.id] ?: 0L }, currency.symbol), style = MaterialTheme.typography.headlineSmall, color = spec.ink)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (currency.isDefault) Text("DEFAULT CURRENCY", style = MaterialTheme.typography.labelSmall, color = spec.accent)
            Text("${group.size} ACCOUNT${if (group.size == 1) "" else "S"}", style = MaterialTheme.typography.labelSmall, color = spec.muted)
          }
        }
      }
      itemsIndexed(group, key = { _, account -> "account:${account.id}" }) { index, account ->
        ManagementListRow(account.name, "",
          { onOpenAccount(account.id) }, R.drawable.ic_tab_dashboard, divider = index > 0, compact = true,
          onManage = { accountMenu = account.id }, detail = {
            val balance = balances[account.id] ?: 0L
            Text(buildAnnotatedString {
              append(compactCurrencyText(balance, currency.symbol))
              if (account.isDefault) {
                append("  ")
                withStyle(SpanStyle(color = spec.accent, fontSize = detailSize, fontWeight = detailWeight)) {
                  append("DEFAULT")
                }
              }
            }, style = MaterialTheme.typography.labelLarge, color = if (balance < 0) spec.expense else spec.ink)
          })
      }
      item("add-account:${currency.id}") {
        Column(Modifier.fillMaxWidth().background(spec.surface).testTag("account-footer:${currency.id}")) {
          if (group.isEmpty()) Text("No accounts in ${currency.code} yet.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = spec.muted)
          BrButton("Add ${currency.code} account", { onAddAccount(currency) }, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            style = BrButtonStyle.OUTLINE, compact = true, iconRes = R.drawable.ic_add)
        }
      }
    }
    if (archived.isNotEmpty()) {
      item("archived-toggle") {
        BrButton(if (showArchived) "Hide archived (${archived.size})" else "Archived accounts (${archived.size})", { showArchived = !showArchived }, style = BrButtonStyle.OUTLINE, modifier = Modifier.padding(top = 12.dp))
      }
      if (showArchived) {
        item("archived-header") { ManagementSection("Archived", archived.size, compact = true) }
        itemsIndexed(archived, key = { _, account -> "archived:${account.id}" }) { index, account ->
          val code = currencies.firstOrNull { it.id == account.currencyId }?.code.orEmpty()
          ManagementListRow(account.name, "$code · Archived", { accountMenu = account.id }, R.drawable.ic_tab_dashboard,
            divider = index > 0, compact = true, onManage = { accountMenu = account.id })
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
