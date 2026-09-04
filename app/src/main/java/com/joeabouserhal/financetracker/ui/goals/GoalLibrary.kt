package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*

@Composable
internal fun GoalLibrary(
  progress: List<GoalProgress>, onAdd: () -> Unit, onCompleted: () -> Unit,
  onEdit: (GoalProgress) -> Unit, onComplete: (GoalProgress) -> Unit,
  modifier: Modifier = Modifier, loaded: Boolean = true,
) {
  val spec = LocalThemeSpec.current
  var query by rememberSaveable { mutableStateOf("") }
  val active = progress.filter { !it.goal.completed }
  val matches = active.filter { it.goal.name.contains(query, true) || it.scopeLabel.contains(query, true) }
  ManagementPage("Goals", "${active.size} active · Track your balances toward a target.", null, modifier,
    listTag = "goal-list", compact = true) {
    item("overview") {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrButton("NEW GOAL", onAdd, Modifier.weight(1f), compact = true, minHeight = 48.dp)
        BrButton("COMPLETED ${progress.count { it.goal.completed }}", onCompleted, Modifier.weight(1f),
          style = BrButtonStyle.OUTLINE, compact = true, minHeight = 48.dp)
      }
      if (active.size > 6 || query.isNotEmpty()) BrTextField(query, { query = it }, "SEARCH GOALS", leadingIconRes = R.drawable.ic_search)
    }
    if (!loaded) item { Text("Loading goals…", color = spec.muted) }
    else if (active.isEmpty()) item {
      ManagementEmptyState(if (progress.isEmpty()) "Make room for your next goal" else "All caught up",
        if (progress.isEmpty()) "Choose a target and track an account balance toward it. Start with something you’re looking forward to."
        else "Your completed goals are saved. Set a new target whenever you’re ready.")
    } else if (matches.isEmpty()) item { ManagementEmptyState("No matching goals", "Try a different name or account.") }
    listOf(true, false).forEach { ready ->
      val group = matches.filter { it.ready == ready }
      if (group.isNotEmpty()) {
        item("section-$ready") { ManagementSection(if (ready) "Ready to complete" else "In progress", group.size, compact = true) }
        items(group, key = { it.goal.id }) { goal ->
          GoalCard(goal, onTap = { onEdit(goal) }, onMarkComplete = { onComplete(goal) })
          Spacer(Modifier.height(6.dp))
        }
      }
    }
    if (active.isNotEmpty()) item("balance-note") {
      Text("Progress follows your account balances. Money isn’t set aside separately for each goal.",
        Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
  }
}

@Composable
internal fun GoalCard(
  progress: GoalProgress,
  onTap: (() -> Unit)? = null,
  onMarkComplete: (() -> Unit)? = null,
  onUndo: (() -> Unit)? = null,
) {
  val spec = LocalThemeSpec.current
  val completed = progress.goal.completed
  val symbol = progress.currency?.symbol.orEmpty()
  Column(Modifier.fillMaxWidth().testTag("goal-${progress.goal.id}")
    .background(spec.surface).border(1.dp, spec.border)) {
    Row(Modifier.fillMaxWidth().then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
      .padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(progress.goal.name, style = MaterialTheme.typography.titleMedium, color = spec.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(progress.scopeLabel, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      if (onTap != null) IconButton(onClick = onTap) {
        Icon(painterResource(R.drawable.ic_more), "Edit ${progress.goal.name}", Modifier.size(20.dp), tint = spec.muted)
      }
      if (completed) Icon(painterResource(R.drawable.ic_check), "Completed", Modifier.padding(horizontal = 8.dp).size(22.dp), tint = spec.income)
    }
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(buildAnnotatedString {
          append(compactCurrencyText(if (completed) progress.goal.targetMinor else progress.progressMinor, symbol))
          withStyle(SpanStyle(color = spec.muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)) {
            if (completed) append(" achieved") else {
              append(" / ")
              append(compactCurrencyText(progress.goal.targetMinor, symbol))
              append(" target")
            }
          }
        }, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.titleMedium, color = spec.ink)
        if (!completed && progress.ready && onMarkComplete != null) Box(
          Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onMarkComplete)
            .padding(vertical = 8.dp), contentAlignment = Alignment.Center,
        ) {
          Box(Modifier.testTag("goal-complete-face").heightIn(min = 32.dp)
            .background(spec.accent).padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center) {
            Text("COMPLETE", style = MaterialTheme.typography.labelSmall, color = spec.onAccent)
          }
        }
        if (completed && onUndo != null) TextButton(onClick = onUndo) {
          Text("UNDO", style = MaterialTheme.typography.labelSmall, color = spec.accent)
        }
      }
      if (!completed) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(Modifier.weight(1f).height(6.dp).background(spec.surfaceAlt)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f) }) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(if (progress.ready) spec.income else spec.accent))
          }
          Text("${progress.percent}%", style = MaterialTheme.typography.labelSmall, color = if (progress.ready) spec.income else spec.accent)
        }
        if (progress.progressMinor < 0) Text("Balance is below zero", style = MaterialTheme.typography.bodySmall, color = spec.expense)
      }
    }
  }
}
