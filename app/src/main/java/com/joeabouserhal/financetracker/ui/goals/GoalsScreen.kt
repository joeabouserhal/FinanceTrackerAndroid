package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
internal fun rememberGoalSnapshot(ownerId: String): GoalSnapshot? {
  val container = rememberAppContainer()
  val snapshot by remember(ownerId) {
    combine(container.goalRepository.observeAll(ownerId), container.transactionRepository.observeAll(ownerId),
      container.currencyRepository.observeAll(ownerId), container.accountRepository.observeAll(ownerId)) { goals, tx, currencies, accounts ->
      GoalSnapshot(buildProgress(goals, tx, currencies, accounts), currencies, accounts, buildAccountBalances(tx, accounts))
    }
  }.collectAsStateWithLifecycle(initialValue = null)
  return snapshot
}

@Composable
fun GoalsScreen(onOpenCompletedGoals: () -> Unit, modifier: Modifier = Modifier) {
  val session by rememberAppContainer().sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  key(session.ownerId) { GoalsPartition(session.ownerId, onOpenCompletedGoals, modifier) }
}

@Composable
private fun GoalsPartition(ownerId: String, onOpenCompletedGoals: () -> Unit, modifier: Modifier) {
  val container = rememberAppContainer()
  val snapshot = rememberGoalSnapshot(ownerId)
  val scope = rememberCoroutineScope()
  var modal by rememberSaveable { mutableStateOf<String?>(null) }
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<CompletionNotice?>(null) }
  val drafts = rememberSaveableStateHolder()
  val selected = snapshot?.progress?.firstOrNull { it.goal.id == selectedId && !it.goal.completed }
  fun dismiss() { if (!busy) { modal = null; error = null } }
  fun runAction(action: suspend () -> Unit) {
    if (busy) return
    busy = true
    error = null
    scope.launch {
      try { action(); modal = null }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { error = e.message ?: "Couldn’t save this change. Please try again." }
      finally { busy = false }
    }
  }
  GoalLibrary(snapshot?.progress.orEmpty(), onAdd = { drafts.removeState("editor"); modal = "add"; selectedId = null; error = null },
    onCompleted = onOpenCompletedGoals,
    onEdit = { drafts.removeState("editor"); selectedId = it.goal.id; modal = "edit"; error = null },
    onComplete = { selectedId = it.goal.id; modal = "complete"; error = null },
    modifier = modifier, loaded = snapshot != null)

  if (snapshot != null && modal != null) {
    if (modal != "add" && selected == null && !busy) {
      BrDialog("GOAL UPDATED", ::dismiss, confirmText = "CLOSE", onConfirm = ::dismiss, dismissText = null) {
        GoalHint("This goal is no longer active. Your list has been refreshed.")
      }
    } else when (modal) {
      "add", "edit" -> drafts.SaveableStateProvider("editor") { GoalEditorDialog(selected?.goal, snapshot.currencies, snapshot.accounts, busy, error, ::dismiss,
        onSave = { name, target, currency, account ->
          runAction {
            if (modal == "add") container.goalRepository.add(ownerId, name, target, currency, account)
            else if (selected != null) container.goalRepository.update(ownerId, selected.goal.id, name, target, currency, account)
          }
        }, onDelete = if (selected != null) ({ modal = "delete"; error = null }) else null) }
      "complete" -> selected?.let { gp ->
        GoalCompletionDialog(gp, snapshot.accounts, snapshot.balances, busy, error, ::dismiss) { allocations ->
          runAction {
            val result = container.goalRepository.complete(ownerId, gp.goal.id, allocations)
            if (result != null) notice = CompletionNotice(result.goalName, gp.currency?.symbol.orEmpty(),
              result.deductions.map { deduction ->
                (snapshot.accounts.firstOrNull { it.id == deduction.accountId }?.name ?: "Account") to deduction.amountMinor
              })
          }
        }
      }
      "delete" -> selected?.let { gp ->
        BrDialog("DELETE GOAL?", { if (!busy) { modal = "edit"; error = null } }, confirmText = if (busy) "DELETING…" else "DELETE", confirmEnabled = !busy,
          scrollContent = true, onConfirm = { runAction { container.goalRepository.delete(ownerId, gp.goal.id) } }) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(gp.goal.name, style = MaterialTheme.typography.titleLarge, color = LocalThemeSpec.current.ink)
            GoalHint("This permanently removes the goal. Your transactions and account balances stay unchanged.")
            GoalError(error)
          }
        }
      }
    }
  }
  notice?.let { GoalSuccessDialog(it) { notice = null } }
}
