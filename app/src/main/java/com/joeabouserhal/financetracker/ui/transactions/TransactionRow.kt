package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.compactCurrencyText

/**
 * One transaction row: category color block, title/category/account on the
 * left, signed amount on the right. Amount color animates with sign.
 */
@Composable
fun TransactionRow(
  item: TransactionListItem,
  onPress: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  val tx = item.transaction
  val isGoal = tx.type == TransactionType.GOAL
  val signed = if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
  val amountColor by animateColorAsState(
    targetValue =
      when (tx.type) {
        TransactionType.INCOME -> spec.income
        TransactionType.GOAL -> spec.goal
        TransactionType.EXPENSE -> spec.expense
      },
    animationSpec = tween(200),
    label = "amountColor",
  )

  Row(
    modifier
      .fillMaxWidth()
      .background(spec.surface)
      .minimumInteractiveComponentSize()
      .then(if (onPress != null) Modifier.clickable(onClick = onPress) else Modifier)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .width(3.dp)
        .height(40.dp)
        .background(if (isGoal) spec.goal else parseColor(item.categoryColor)),
    )
    Column(
      Modifier
        .weight(1f)
        .padding(horizontal = 11.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = tx.title ?: item.categoryName,
        style = MaterialTheme.typography.bodyMedium,
        color = spec.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
          if (isGoal) "Goal" else item.categoryName.takeIf { tx.title != null },
          item.accountName,
        ).joinToString(" · ")
      if (meta.isNotBlank()) {
        Text(meta, style = MaterialTheme.typography.labelSmall, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = compactCurrencyText(
          kotlin.math.abs(signed),
          item.currencySymbol,
          prefix = if (tx.type == TransactionType.INCOME) "+" else "-",
        ),
        style = MaterialTheme.typography.labelLarge,
        color = amountColor,
      )
      Text(tx.date, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    }
  }
}

/** Subtle separator used only between adjacent rows in a transaction block. */
@Composable
fun TransactionDashedDivider(modifier: Modifier = Modifier) {
  val spec = LocalThemeSpec.current
  Canvas(modifier.fillMaxWidth().height(1.dp).background(spec.surface)) {
    val centerY = size.height / 2f
    val inset = 12.dp.toPx()
    drawLine(
      color = spec.border.copy(alpha = 0.42f),
      start = Offset(inset, centerY),
      end = Offset(size.width - inset, centerY),
      strokeWidth = 1.dp.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
      cap = StrokeCap.Butt,
    )
  }
}

private fun parseColor(hex: String): Color =
  try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (_: IllegalArgumentException) {
    Color(0xFF77746C)
  }
