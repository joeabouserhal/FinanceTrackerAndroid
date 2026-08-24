package com.joeabouserhal.financetracker.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.repositories.CurrencyBalance
import com.joeabouserhal.financetracker.data.repositories.DashboardData
import com.joeabouserhal.financetracker.data.repositories.MonthlyActivity
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrFab
import com.joeabouserhal.financetracker.ui.components.EmptyState
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.transactions.TransactionRow
import com.joeabouserhal.financetracker.utils.Dates
import com.joeabouserhal.financetracker.utils.Money
import java.time.YearMonth

/**
 * Flat dashboard: BALANCE / ACTIVITY / RECENT separated by hairlines, no
 * cards. Balance is a per-currency table — total on top in big type, accounts
 * beneath in smaller type, every number right-aligned.
 */
@Composable
fun DashboardScreen(
  onAddTransaction: () -> Unit,
  onEditTransaction: (String) -> Unit,
  onSeeAllTransactions: () -> Unit,
) {
  val container = rememberAppContainer()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var monthIso by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
  val month = YearMonth.parse(monthIso)
  val (from, to) = Dates.monthBounds(month)

  val data by remember(ownerId, from, to) { container.dashboardRepository.observe(ownerId, from, to) }
    .collectAsStateWithLifecycle(initialValue = DashboardData(emptyList(), emptyList(), emptyList()))

  val spec = LocalThemeSpec.current

  Column(Modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Finances")

    Box(Modifier.weight(1f)) {
      Column(
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          BalanceSection(data.balances)
        }
        SectionDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          ActivitySection(
            month = month,
            onPrevious = { monthIso = month.minusMonths(1).toString() },
            onNext = { monthIso = month.plusMonths(1).toString() },
            monthly = data.monthly,
          )
        }
        SectionDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
          RecentSection(data.recent, onEditTransaction, onSeeAllTransactions)
        }
        Spacer(Modifier.height(80.dp))
      }

      BrFab(
        onClick = onAddTransaction,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
  val spec = LocalThemeSpec.current
  Text(text, style = MaterialTheme.typography.labelMedium, color = spec.accent)
}

@Composable
private fun SectionDivider() {
  val spec = LocalThemeSpec.current
  Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.45f)))
}

@Composable
private fun ThinDivider() {
  val spec = LocalThemeSpec.current
  Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.25f)))
}

/** Receipt-style hairline: a thin dashed rule in the border color. */
@Composable
private fun DashedDivider() {
  val spec = LocalThemeSpec.current
  val color = spec.border.copy(alpha = 0.4f)
  Canvas(Modifier.fillMaxWidth().height(1.dp)) {
    drawLine(
      color = color,
      start = Offset(0f, size.height / 2f),
      end = Offset(size.width, size.height / 2f),
      strokeWidth = 1.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
    )
  }
}

// ---------------------------------------------------------------------------
// 1. Balance — per-currency table, totals big, all numbers right-aligned
// ---------------------------------------------------------------------------

@Composable
private fun BalanceSection(balances: List<CurrencyBalance>) {
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    SectionLabel("BALANCE")
    if (balances.isEmpty()) {
      EmptyState(message = "No accounts yet — add a currency & account in Settings")
    } else {
      balances.forEachIndexed { index, balance ->
        CurrencyBalanceBlock(balance)
        // One receipt-style dashed rule between currency groups only.
        if (index < balances.lastIndex) DashedDivider()
      }
    }
  }
}

@Composable
private fun CurrencyBalanceBlock(balance: CurrencyBalance) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // Currency group header: quiet caption, nothing that competes with the total.
    Text(
      "${balance.currency.code} · ${balance.currency.name}",
      style = MaterialTheme.typography.labelSmall,
      color = spec.muted,
    )

    // Total row: label left, big number right with a quieter symbol.
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "TOTAL",
        style = MaterialTheme.typography.bodyMedium,
        color = spec.ink,
      )
      val totalColor by animateColorAsState(
        targetValue = if (balance.totalMinor < 0) spec.expense else spec.ink,
        label = "totalAmount",
      )
      Row {
        Text(
          balance.currency.symbol,
          style = MaterialTheme.typography.labelLarge,
          color = spec.muted,
          modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(4.dp))
        Text(
          Money.format(balance.totalMinor, ""),
          style = MaterialTheme.typography.headlineLarge,
          color = totalColor,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.alignByBaseline(),
        )
      }
    }

    // Accounts beneath: smaller text than the total row, amounts right-aligned.
    // A single account adds no breakdown value — the total already IS it.
    if (balance.accounts.size > 1) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        balance.accounts.forEach { account ->
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              account.account.name,
              style = MaterialTheme.typography.bodySmall,
              color = spec.ink,
            )
            val amountColor by animateColorAsState(
              targetValue = if (account.balanceMinor < 0) spec.expense else spec.ink,
              label = "accountAmount",
            )
            Text(
              Money.format(account.balanceMinor, account.currency.symbol),
              style = MaterialTheme.typography.labelMedium,
              color = amountColor,
            )
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 2. Activity — month switcher + income/expense bars per currency
// ---------------------------------------------------------------------------

@Composable
private fun ActivitySection(
  month: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  monthly: List<MonthlyActivity>,
) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionLabel("ACTIVITY")

    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      MonthArrow("<", onPrevious)
      AnimatedContent(
        targetState = month,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "monthLabel",
      ) { m ->
        Text(
          Dates.monthLabel(m).uppercase(),
          style = MaterialTheme.typography.labelLarge,
          color = spec.ink,
          fontWeight = FontWeight.Bold,
        )
      }
      MonthArrow(">", onNext, enabled = month < YearMonth.now())
    }

    if (monthly.isEmpty()) {
      EmptyState(message = "No activity this month")
    } else {
      monthly.forEach { activity -> ActivityBlock(activity) }
    }
  }
}

@Composable
private fun MonthArrow(label: String, onClick: () -> Unit, enabled: Boolean = true) {
  val spec = LocalThemeSpec.current
  Text(
    text = label,
    style = MaterialTheme.typography.headlineSmall,
    color = if (enabled) spec.accent else spec.muted.copy(alpha = 0.4f),
    modifier = Modifier
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .minimumInteractiveComponentSize()
      .clickable(enabled = enabled, onClick = onClick),
  )
}

@Composable
private fun ActivityBlock(activity: MonthlyActivity) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(
        activity.currency.code,
        style = MaterialTheme.typography.labelLarge,
        color = spec.ink,
        fontWeight = FontWeight.Bold,
      )
      Text(
        activity.currency.name.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
      )
    }

    val hasIncome = activity.incomeMinor > 0
    val hasExpense = activity.expenseMinor > 0
    val incomeFraction =
      if (hasIncome && hasExpense) {
        activity.incomeMinor.toFloat() / (activity.incomeMinor + activity.expenseMinor)
      } else {
        0f
      }
    val animatedFraction by animateFloatAsState(
      targetValue = incomeFraction,
      animationSpec = tween(500),
      label = "incomeFraction",
    )

    // No zero-width slivers: a side with no activity gets no strip at all.
    Row(Modifier.fillMaxWidth().height(14.dp)) {
      when {
        hasIncome && hasExpense -> {
          Box(Modifier.weight(animatedFraction).fillMaxSize().background(spec.income))
          Box(Modifier.weight(1f - animatedFraction).fillMaxSize().background(spec.expense))
        }
        hasIncome -> Box(Modifier.weight(1f).fillMaxSize().background(spec.income))
        hasExpense -> Box(Modifier.weight(1f).fillMaxSize().background(spec.expense))
        else -> Box(Modifier.weight(1f).fillMaxSize().background(spec.border.copy(alpha = 0.45f)))
      }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      if (hasIncome) {
        Text(
          "+${Money.format(activity.incomeMinor, activity.currency.symbol)}",
          style = MaterialTheme.typography.labelMedium,
          color = spec.income,
        )
      } else {
        Spacer(Modifier.width(1.dp))
      }
      if (hasExpense) {
        Text(
          "-${Money.format(activity.expenseMinor, activity.currency.symbol)}",
          style = MaterialTheme.typography.labelMedium,
          color = spec.expense,
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// 3. Recent — list with hairline separators
// ---------------------------------------------------------------------------

@Composable
private fun RecentSection(
  recent: List<TransactionListItem>,
  onEdit: (String) -> Unit,
  onSeeAll: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("RECENT")
    }
    if (recent.isEmpty()) {
      EmptyState(message = "No transactions yet. Tap + Add to create one.")
    } else {
      Column {
        recent.take(5).forEachIndexed { index, item ->
          TransactionRow(item = item, onPress = { onEdit(item.transaction.id) })
          if (index < recent.take(5).lastIndex) DashedDivider()
        }
      }
      BrButton(
        text = "VIEW MORE",
        onClick = onSeeAll,
        style = BrButtonStyle.OUTLINE,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
