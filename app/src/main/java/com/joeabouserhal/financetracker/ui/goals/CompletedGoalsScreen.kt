package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Pushed page: the goals that were marked complete, with an UNDO action. */
@Composable
fun CompletedGoalsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  val progress by remember(ownerId) {
    combine(
      container.goalRepository.observeAll(ownerId),
      container.transactionRepository.observeAll(ownerId),
      container.currencyRepository.observeAll(ownerId),
      // All accounts so archived-account goals keep their real scope label.
      container.accountRepository.observeAll(ownerId),
    ) { goals, transactions, currencies, accounts ->
      buildProgress(goals, transactions, currencies, accounts).filter { it.goal.completed }
    }
  }.collectAsStateWithLifecycle(initialValue = emptyList())

  Column(modifier.fillMaxSize().background(spec.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "< BACK",
        style = MaterialTheme.typography.labelMedium,
        color = spec.accent,
        modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack),
      )
    }
    ScreenHeader(title = "Completed goals", subtitle = "TAP UNDO IF YOU MARKED ONE BY ACCIDENT")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (progress.isEmpty()) {
        Text("Nothing completed yet.", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
      }
      progress.forEach { gp ->
        GoalCard(
          gp,
          completed = true,
          onTap = null,
          onMarkComplete = null,
          onUndo = {
            scope.launch { container.goalRepository.markActive(ownerId, gp.goal.id) }
          },
        )
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
