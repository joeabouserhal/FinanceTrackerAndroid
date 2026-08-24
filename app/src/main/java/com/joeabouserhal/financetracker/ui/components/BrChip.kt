package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Selectable brutalist chip; pass [colorDot] for color-coded categories. */
@Composable
fun BrChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  colorDot: Color? = null,
) {
  val spec = LocalThemeSpec.current
  val face = if (selected) spec.accent else spec.surface
  val content = if (selected) spec.onAccent else spec.ink

  Row(
    modifier =
      modifier
        .background(face)
        .border(spec.borderWidth, if (selected) spec.accent else spec.border)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (colorDot != null) {
      Box(Modifier.size(12.dp).background(colorDot))
    }
    Text(
      text = text.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = content,
    )
  }
}
