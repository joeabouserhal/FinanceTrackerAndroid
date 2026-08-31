package com.joeabouserhal.financetracker.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Progress snapshot for one goal, computed from transactions. */
internal data class GoalProgress(
  val goal: GoalEntity,
  val currency: CurrencyEntity?,
  val account: AccountEntity?,
  val progressMinor: Long,
)

@Composable
fun GoalsScreen(
  onOpenCompletedGoals: () -> Unit,
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
      container.accountRepository.observeActive(ownerId),
    ) { goals, transactions, currencies, accounts ->
      buildProgress(goals, transactions, currencies, accounts)
    }
  }.collectAsStateWithLifecycle(initialValue = emptyList())

  var adding by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<GoalEntity?>(null) }
  var deleting by remember { mutableStateOf<GoalEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  val activeGoals = progress.filter { !it.goal.completed }
  val completedGoals = progress.filter { it.goal.completed }

  Column(modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Goals", subtitle = "SAVE TOWARD A TARGET — WE TRACK THE PERCENTAGE")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }

      BrButton(text = "+ Add goal", onClick = { adding = true })

      if (completedGoals.isNotEmpty()) {
        BrButton(
          text = "VIEW COMPLETED GOALS (${completedGoals.size})",
          onClick = onOpenCompletedGoals,
          style = BrButtonStyle.OUTLINE,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (progress.isEmpty()) {
        Text(
          "No goals yet — set one and watch it fill up.",
          style = MaterialTheme.typography.bodyMedium,
          color = spec.muted,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        activeGoals.forEach { gp ->
          GoalCard(
            gp,
            completed = false,
            onTap = { editing = gp.goal },
            onMarkComplete = {
              scope.launch {
                container.goalRepository.markComplete(ownerId, gp.goal.id)
              }
            },
          )
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }

  if (adding) {
    GoalDialog(
      title = "ADD GOAL",
      initial = null,
      onDismiss = { adding = false },
      onSave = { name, targetMinor, currencyId, accountId ->
        scope.launch {
          try {
            container.goalRepository.add(ownerId, name, targetMinor, currencyId, accountId)
            adding = false
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editing?.let { goal ->
    GoalDialog(
      title = "EDIT GOAL",
      initial = goal,
      onDismiss = { editing = null },
      onSave = { name, targetMinor, currencyId, accountId ->
        scope.launch {
          try {
            container.goalRepository.update(ownerId, goal.id, name, targetMinor, currencyId, accountId)
            editing = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
      onDelete = { deleting = goal },
    )
  }

  deleting?.let { goal ->
    BrDialog(
      title = "DELETE GOAL?",
      onDismiss = { deleting = null },
      confirmText = "DELETE",
      onConfirm = {
        scope.launch {
          container.goalRepository.delete(ownerId, goal.id)
          deleting = null
          editing = null
        }
      },
    ) {
      Text("This goal disappears everywhere.", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

internal fun buildProgress(
  goals: List<GoalEntity>,
  transactions: List<TransactionEntity>,
  currencies: List<CurrencyEntity>,
  accounts: List<AccountEntity>,
): List<GoalProgress> {
  val currencyById = currencies.associateBy { it.id }
  val accountById = accounts.associateBy { it.id }
  val netByAccount = HashMap<String, Long>()
  val netByCurrency = HashMap<String, Long>()
  for (t in transactions) {
    val signed = if (t.type == TransactionType.INCOME) t.amount else -t.amount
    t.accountId?.let { netByAccount[it] = (netByAccount[it] ?: 0L) + signed }
    netByCurrency[t.currencyId] = (netByCurrency[t.currencyId] ?: 0L) + signed
  }
  return goals.map { goal ->
    val progressMinor = goal.accountId?.let { netByAccount[it] ?: 0L } ?: (netByCurrency[goal.currencyId] ?: 0L)
    GoalProgress(goal, currencyById[goal.currencyId], goal.accountId?.let { accountById[it] }, progressMinor)
  }
}

@Composable
internal fun GoalCard(
  progress: GoalProgress,
  completed: Boolean,
  onTap: (() -> Unit)?,
  onMarkComplete: (() -> Unit)?,
  onUndo: (() -> Unit)? = null,
) {
  val spec = LocalThemeSpec.current
  val symbol = progress.currency?.symbol ?: ""
  val target = progress.goal.targetMinor
  val fraction = if (target > 0) progress.progressMinor.toFloat() / target.toFloat() else 0f
  val percent = (fraction * 100).roundToInt()
  val achieved = progress.progressMinor >= target
  val scopeLabel =
    progress.account?.let { "${it.name} · ${progress.currency?.code ?: ""}" }
      ?: "All ${progress.currency?.code ?: ""} accounts"

  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(progress.goal.name, style = MaterialTheme.typography.bodyLarge, color = if (completed) spec.muted else spec.ink)
      if (completed) {
        Text("COMPLETED", style = MaterialTheme.typography.labelMedium, color = spec.income, fontWeight = FontWeight.Bold)
      } else {
        Text(
          "$percent%",
          style = MaterialTheme.typography.titleMedium,
          color = if (achieved) spec.income else spec.accent,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Text(scopeLabel, style = MaterialTheme.typography.labelMedium, color = spec.muted)
    Box(Modifier.fillMaxWidth().height(12.dp).background(spec.surfaceAlt)) {
      Box(
        Modifier
          .fillMaxHeight()
          .fillMaxWidth(fraction.coerceIn(0f, 1f))
          .background(if (achieved || completed) spec.income else spec.accent),
      )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${Money.format(progress.progressMinor, symbol)} / ${Money.format(target, symbol)}",
        style = MaterialTheme.typography.bodyMedium,
        color = spec.muted,
      )
      if (!completed && achieved && onMarkComplete != null) {
        BrButton(
          text = "COMPLETE",
          onClick = onMarkComplete,
          style = BrButtonStyle.OUTLINE,
          compact = true,
          iconRes = R.drawable.ic_check,
          iconSize = 20.dp,
          trailingIcon = true,
          fillWidth = false,
        )
      }
      if (completed && onUndo != null) {
        BrButton(
          text = "UNDO",
          onClick = onUndo,
          style = BrButtonStyle.OUTLINE,
          compact = true,
          fillWidth = false,
        )
      }
    }
  }
}

@Composable
private fun GoalDialog(
  title: String,
  initial: GoalEntity?,
  onDismiss: () -> Unit,
  onSave: (name: String, targetMinor: Long, currencyId: String, accountId: String?) -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var name by remember { mutableStateOf(initial?.name ?: "") }
  var targetText by remember {
    mutableStateOf(
      initial?.targetMinor?.let { minor ->
        if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
      } ?: "",
    )
  }
  var currencyId by remember { mutableStateOf(initial?.currencyId) }
  var accountId by remember { mutableStateOf(initial?.accountId) }
  var dialogError by remember { mutableStateOf<String?>(null) }

  // Defaults for new goals: default currency + its default account... but a
  // goal usually spans the whole currency — preselect currency only.
  androidx.compose.runtime.LaunchedEffect(currencies) {
    if (initial == null && currencyId == null) {
      currencyId = currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id
    }
  }

  val currencyAccounts = accounts.filter { it.currencyId == currencyId }
  val duplicateNames = currencyAccounts.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    onConfirm = {
      val trimmedName = name.trim()
      val targetMinor =
        targetText.trim().takeIf { it.isNotBlank() }?.let {
          try { BigDecimal(Money.normalizeDecimalInput(it)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
      when {
        trimmedName.isBlank() -> dialogError = "Goal name is required"
        targetMinor == null || targetMinor <= 0 -> dialogError = "Target must be a positive amount"
        currencyId == null -> dialogError = "Pick a currency"
        else -> onSave(trimmedName, targetMinor, currencyId!!, accountId)
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      BrTextField(name, { name = it }, "GOAL NAME")
      BrTextField(
        targetText,
        { targetText = it },
        "TARGET AMOUNT",
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        suffix = {
          currencies.firstOrNull { it.id == currencyId }?.symbol?.let { symbol ->
            Text(symbol, style = MaterialTheme.typography.bodyLarge, color = spec.muted)
          }
        },
      )
      dialogError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = spec.expense) }

      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c ->
          BrChip(
            c.code,
            selected = currencyId == c.id,
            large = true,
            onClick = {
              currencyId = c.id
              if (accountId != null && accounts.none { it.id == accountId && it.currencyId == c.id }) accountId = null
            },
          )
        }
      }

      Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrChip("All", selected = accountId == null, large = true, onClick = { accountId = null })
        currencyAccounts.forEach { a ->
          val suffix = if (a.name in duplicateNames) currencies.firstOrNull { it.id == a.currencyId }?.code else null
          BrChip(
            a.name,
            suffix = suffix,
            selected = accountId == a.id,
            large = true,
            onClick = { accountId = a.id },
          )
        }
      }

      if (initial != null && onDelete != null) {
        Text(
          "DELETE GOAL",
          style = MaterialTheme.typography.labelMedium,
          color = spec.expense,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onDelete)
            .padding(vertical = 4.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }
}
