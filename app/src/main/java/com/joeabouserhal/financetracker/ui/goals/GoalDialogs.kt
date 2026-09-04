package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*
import com.joeabouserhal.financetracker.utils.Money

@Composable
internal fun GoalEditorDialog(
  initial: GoalEntity?, currencies: List<CurrencyEntity>, accounts: List<AccountEntity>,
  busy: Boolean, error: String?, onDismiss: () -> Unit,
  onSave: (String, Long, String, String?) -> Unit, onDelete: (() -> Unit)? = null,
) {
  val spec = LocalThemeSpec.current
  var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
  var amount by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetMinor?.let(::minorToText).orEmpty()) }
  var currencyId by rememberSaveable(initial?.id) { mutableStateOf(initial?.currencyId ?: currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id) }
  var accountId by rememberSaveable(initial?.id) { mutableStateOf(initial?.accountId) }
  var validation by remember { mutableStateOf<String?>(null) }
  val currency = currencies.firstOrNull { it.id == currencyId }
  val options = accounts.filter { it.currencyId == currencyId && (!it.archived || it.id == initial?.accountId) }
  BrDialog(if (initial == null) "NEW GOAL" else "EDIT GOAL", onDismiss,
    confirmText = if (busy) "SAVING…" else "SAVE GOAL", confirmEnabled = !busy, scrollContent = true,
    onConfirm = {
      val target = textToMinor(amount)
      validation = when {
        name.isBlank() -> "Give your goal a name."
        target <= 0 -> "Enter a valid target greater than zero."
        currency == null -> "Choose an available currency."
        accountId != null && options.none { it.id == accountId } -> "Choose an available account."
        else -> null
      }
      if (validation == null) onSave(name.trim(), target, currencyId!!, accountId)
    }) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      BrTextField(name, { name = it }, "GOAL NAME", enabled = !busy)
      BrTextField(amount, { amount = it }, "TARGET AMOUNT", enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text(currency?.symbol.orEmpty(), color = spec.muted) })
      GoalFieldGroup("CURRENCY") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          currencies.sortedByDescending { it.isDefault }.forEach { c ->
            BrChip(c.code, c.id == currencyId, { if (!busy && currencyId != c.id) { currencyId = c.id; accountId = null } })
          }
        }
        if (currencies.isEmpty()) Text("Add a currency in Settings first.", color = spec.expense, style = MaterialTheme.typography.bodySmall)
      }
      GoalFieldGroup("TRACK BALANCE FROM") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          BrChip("All accounts", accountId == null, { if (!busy) accountId = null })
          options.forEach { a ->
            BrChip(a.name + if (a.archived) " (archived)" else "", a.id == accountId, { if (!busy) accountId = a.id })
          }
        }
      }
      GoalHint("This tracks ${if (accountId == null) "the combined balance in ${currency?.code ?: "this currency"}" else "this account’s balance"}. It doesn’t reserve money or create a transaction until you complete the goal.")
      GoalError(validation ?: error)
      if (onDelete != null) TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("DELETE GOAL", color = spec.expense, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
internal fun GoalCompletionDialog(
  progress: GoalProgress, accounts: List<AccountEntity>, balances: Map<String, Long>,
  busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (Map<String, Long>) -> Unit,
) {
  val spec = LocalThemeSpec.current
  val goal = progress.goal
  val symbol = progress.currency?.symbol.orEmpty()
  val eligible = accounts.filter { it.currencyId == goal.currencyId &&
    if (goal.accountId != null) it.id == goal.accountId else !it.archived }
  // Save only drafts, not account snapshots: incoming balances/names must not erase typed allocations.
  var values by rememberSaveable(goal.id, goal.targetMinor) {
    mutableStateOf(HashMap(if (goal.accountId != null) mapOf(goal.accountId to minorToText(goal.targetMinor))
      else suggestAllocations(goal.targetMinor, eligible, balances).mapValues { minorToText(it.value) }))
  }
  val parsed = eligible.associate { it.id to textToMinor(values[it.id].orEmpty()) }
  val total = allocationTotal(parsed.values)
  val exact = total == goal.targetMinor && eligible.isNotEmpty()
  val overdrawn = eligible.filter { (parsed[it.id] ?: 0) > (balances[it.id] ?: 0).coerceAtLeast(0) }
  BrDialog("COMPLETE GOAL", onDismiss, confirmText = if (busy) "COMPLETING…" else "COMPLETE",
    confirmEnabled = exact && progress.ready && !busy, scrollContent = true,
    onConfirm = { onConfirm(parsed) }) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(goal.name, style = MaterialTheme.typography.titleLarge, color = spec.ink)
      GoalFieldGroup("TOTAL TO WITHDRAW") {
        Text(compactCurrencyText(goal.targetMinor, symbol), style = MaterialTheme.typography.headlineSmall, color = spec.ink)
      }
      GoalHint("Completing this goal records withdrawals from the accounts below. Review the amounts before confirming.")
      if (eligible.isEmpty()) GoalError("No available account in this currency. Add or reactivate an account first.")
      if (goal.accountId == null && eligible.isNotEmpty()) {
        TextButton(onClick = { values = HashMap(suggestAllocations(goal.targetMinor, eligible, balances).mapValues { minorToText(it.value) }) }, enabled = !busy) {
          Text("USE AVAILABLE BALANCES", style = MaterialTheme.typography.labelSmall, color = spec.accent)
        }
      }
      eligible.forEach { account ->
        GoalFieldGroup(account.name) {
          Text(compactCurrencyText(balances[account.id] ?: 0, symbol, "Available "), style = MaterialTheme.typography.bodySmall, color = spec.muted)
          if (goal.accountId != null) Text(compactCurrencyText(goal.targetMinor, symbol, "Withdraw "), style = MaterialTheme.typography.titleMedium, color = spec.ink)
          else BrTextField(values[account.id].orEmpty(), { value -> values = HashMap(values).apply { put(account.id, value) } },
            "WITHDRAW AMOUNT", enabled = !busy, modifier = Modifier.testTag("allocation-${account.id}"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text(symbol, color = spec.muted) })
        }
      }
      if (goal.accountId == null && eligible.isNotEmpty()) {
        Text(when {
          total == null -> "Enter valid, non-negative amounts within the supported range."
          total < goal.targetMinor -> "${Money.format(goal.targetMinor - total, symbol)} left to assign"
          total > goal.targetMinor -> "${Money.format(total - goal.targetMinor, symbol)} over the target"
          else -> "All amounts assigned"
        }, style = MaterialTheme.typography.bodyMedium, color = if (exact) spec.income else spec.expense)
      }
      if (overdrawn.isNotEmpty()) GoalError("This will leave ${overdrawn.joinToString { it.name }} below zero.")
      if (!progress.ready && !busy) GoalError("The balance has changed and no longer meets this target. Close this review and check your goal.")
      GoalError(error)
    }
  }
}

@Composable
internal fun GoalUndoDialog(name: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  BrDialog("UNDO COMPLETION?", onDismiss, confirmText = if (busy) "UNDOING…" else "UNDO", confirmEnabled = !busy,
    onConfirm = onConfirm, scrollContent = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(name, style = MaterialTheme.typography.titleLarge, color = LocalThemeSpec.current.ink)
      GoalHint("This removes the goal’s withdrawal transactions, restores those account balances, and moves the goal back to your active list.")
      GoalError(error)
    }
  }
}

@Composable
internal fun GoalSuccessDialog(notice: CompletionNotice, onDismiss: () -> Unit) {
  val spec = LocalThemeSpec.current
  BrDialog("GOAL COMPLETED", onDismiss, confirmText = "DONE", onConfirm = onDismiss, dismissText = null, scrollContent = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Made it happen.", style = MaterialTheme.typography.headlineSmall, color = spec.income)
      Text(notice.goalName, style = MaterialTheme.typography.titleLarge, color = spec.ink)
      GoalFieldGroup("WITHDRAWALS RECORDED") {
        notice.deductions.forEach { (name, amount) ->
          Text(name, style = MaterialTheme.typography.bodySmall, color = spec.muted)
          Text(compactCurrencyText(amount, notice.symbol), style = MaterialTheme.typography.titleMedium, color = spec.ink)
        }
      }
      GoalHint("Made a mistake? Undo completion from your completed goals.")
    }
  }
}

@Composable
private fun GoalFieldGroup(label: String, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = LocalThemeSpec.current.muted)
    content()
  }
}

@Composable
internal fun GoalHint(text: String) {
  val spec = LocalThemeSpec.current
  Text(text, Modifier.fillMaxWidth().background(spec.surfaceAlt).padding(12.dp), style = MaterialTheme.typography.bodySmall, color = spec.muted)
}

@Composable
internal fun GoalError(error: String?) {
  if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = LocalThemeSpec.current.expense)
}
