package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
  large: Boolean = false,
  comfortable: Boolean = false,
  suffix: String? = null,
  enabled: Boolean = true,
) {
  val spec = LocalThemeSpec.current
  val modal = LocalModalStyle.current
  val face = if (!enabled) spec.surfaceAlt else if (selected) spec.accent else if (modal) Color.Transparent else spec.surface
  val content = if (!enabled) spec.muted.copy(alpha = 0.6f) else if (selected) spec.onAccent else spec.ink

  Row(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .background(face)
        .then(if (modal && selected && enabled) Modifier else Modifier.border(spec.borderWidth,
          if (!enabled) spec.border.copy(alpha = 0.45f) else if (selected) spec.accent else spec.border))
        .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
        .padding(
          horizontal = when {
            large -> 16.dp
            comfortable -> 12.dp
            else -> 10.dp
          },
          vertical = when {
            large -> 12.dp
            comfortable -> 7.dp
            else -> 6.dp
          },
        ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (colorDot != null) {
      Box(Modifier.size(if (large) 14.dp else if (comfortable) 13.dp else 12.dp).background(if (enabled) colorDot else colorDot.copy(alpha = 0.45f)))
      Spacer(Modifier.width(6.dp))
    }
    Text(
      text = text.uppercase(),
      style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
      color = content,
    )
    if (suffix != null) {
      Spacer(Modifier.width(4.dp))
      Text(
        text = "(${suffix.uppercase()})",
        style = MaterialTheme.typography.labelSmall,
        color = if (!enabled || selected) content.copy(alpha = 0.72f) else spec.muted,
      )
    }
  }
}
