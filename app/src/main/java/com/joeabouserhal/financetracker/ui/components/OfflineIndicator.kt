package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Small status pill. Red/expense when offline, green/income when online. */
@Composable
fun OfflineIndicator(
  isOffline: Boolean,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  val color = if (isOffline) spec.expense else spec.income
  Text(
    text = if (isOffline) "OFFLINE" else "ONLINE",
    style = MaterialTheme.typography.labelSmall,
    color = color,
    modifier =
      modifier
        .background(spec.surface)
        .border(spec.borderWidth, color)
        .padding(horizontal = 8.dp, vertical = 4.dp),
  )
}
