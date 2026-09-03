package com.joeabouserhal.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Structured floating add menu. The expanded state reads as one compact action
 * tray instead of a loose pile of unrelated buttons.
 */
@Composable
fun ExpandableFab(
  onAddTransaction: () -> Unit,
  onAddFromPreset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(tween(190), expandFrom = Alignment.Bottom) + fadeIn(tween(150)),
      exit = shrinkVertically(tween(150), shrinkTowards = Alignment.Bottom) + fadeOut(tween(110)),
    ) {
      ActionTray(
        onAddTransaction = {
          expanded = false
          onAddTransaction()
        },
        onAddFromPreset = {
          expanded = false
          onAddFromPreset()
        },
      )
    }

    BrFab(
      onClick = { expanded = !expanded },
      expanded = expanded,
      contentDescription = if (expanded) "Close add menu" else "Open add menu",
    )
  }
}

@Composable
private fun ActionTray(
  onAddTransaction: () -> Unit,
  onAddFromPreset: () -> Unit,
) {
  Column(
    Modifier.width(244.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    ActionRow(
      iconRes = R.drawable.ic_tab_presets,
      title = "FROM PRESET",
      detail = "REUSE SAVED DETAILS",
      onClick = onAddFromPreset,
    )
    ActionRow(
      iconRes = R.drawable.ic_tab_transactions,
      title = "TRANSACTION",
      detail = "START FROM SCRATCH",
      onClick = onAddTransaction,
    )
  }
}

@Composable
private fun ActionRow(
  @DrawableRes iconRes: Int,
  title: String,
  detail: String,
  onClick: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val tone = spec.accent
  val onTone = spec.onAccent
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  Row(
    Modifier
      .fillMaxWidth()
      .background(if (pressed) tone.copy(alpha = 0.80f) else tone)
      .border(spec.borderWidth, spec.border)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      )
      .padding(horizontal = 12.dp, vertical = 11.dp),
    horizontalArrangement = Arrangement.spacedBy(11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(38.dp).background(onTone.copy(alpha = 0.12f)).border(spec.borderWidth, onTone.copy(alpha = 0.45f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = onTone,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.labelMedium, color = onTone, fontWeight = FontWeight.Bold)
      Text(detail, style = MaterialTheme.typography.labelSmall, color = onTone.copy(alpha = 0.72f))
    }
    Icon(
      painter = painterResource(R.drawable.ic_chevron_right),
      contentDescription = null,
      tint = onTone.copy(alpha = 0.72f),
      modifier = Modifier.size(18.dp),
    )
  }
}
