package com.joeabouserhal.financetracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Expanding FAB menu: tap + to reveal the two add actions above the FAB
 * (the + flips to ✕), tap ✕ or pick an action to collapse.
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
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(tween(180), expandFrom = Alignment.Bottom) + fadeIn(tween(180)),
      exit = shrinkVertically(tween(150), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150)),
    ) {
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FabOption("Add from preset") {
          expanded = false
          onAddFromPreset()
        }
        FabOption("Add transaction") {
          expanded = false
          onAddTransaction()
        }
      }
    }
    BrFab(
      onClick = { expanded = !expanded },
      showCloseIcon = expanded,
      contentDescription = if (expanded) "Close add menu" else "Add transaction",
    )
  }
}

@Composable
private fun FabOption(label: String, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Row(
    Modifier
      .background(spec.accent)
      .border(spec.borderWidth, spec.border)
      .clickable(onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("+", style = MaterialTheme.typography.labelLarge, color = spec.onAccent)
    Box(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = spec.onAccent)
  }
}
