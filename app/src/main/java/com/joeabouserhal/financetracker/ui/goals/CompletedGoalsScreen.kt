package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.ui.components.*
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CompletedGoalsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val session by rememberAppContainer().sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  key(session.ownerId) { CompletedGoalsPartition(session.ownerId, onBack, modifier) }
}

@Composable
private fun CompletedGoalsPartition(ownerId: String, onBack: () -> Unit, modifier: Modifier) {
  val container = rememberAppContainer()
  val snapshot = rememberGoalSnapshot(ownerId)
  val scope = rememberCoroutineScope()
  var undoId by rememberSaveable { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val completed = snapshot?.progress.orEmpty().filter { it.goal.completed }
  val pending = completed.firstOrNull { it.goal.id == undoId }
  ManagementPage("Completed goals", "Targets reached. Keep the record of what you’ve accomplished.", onBack, modifier,
    compact = true, listTag = "completed-goal-list") {
    if (snapshot == null) item { Text("Loading goals…") }
    else if (completed.isEmpty()) item { ManagementEmptyState("Your next milestone awaits", "Completed goals will appear here. Head back to your active goals to keep going.") }
    else {
      item { ManagementSection("Achieved", completed.size, compact = true) }
      items(completed, key = { it.goal.id }) { gp ->
        GoalCard(gp, onUndo = { undoId = gp.goal.id; error = null })
        Spacer(Modifier.height(10.dp))
      }
    }
  }
  if (pending != null) GoalUndoDialog(pending.goal.name, busy, error, { if (!busy) { undoId = null; error = null } }) {
    if (!busy) {
      busy = true
      error = null
      scope.launch {
        try { container.goalRepository.markActive(ownerId, pending.goal.id); undoId = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Couldn’t undo completion. Please try again." }
        finally { busy = false }
      }
    }
  }
}
