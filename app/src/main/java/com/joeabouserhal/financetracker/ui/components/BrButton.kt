package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

enum class BrButtonStyle { SOLID, OUTLINE, INK, DANGER }

/**
 * Chunky brutalist button with an offset hard shadow and mono label.
 * The face Box measures normally (fillMaxWidth + padding), so the button
 * always has real height; only the shadow layer uses matchParentSize.
 * Pass [iconRes] to show a small leading icon (e.g. the Google G).
 */
@Composable
fun BrButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  style: BrButtonStyle = BrButtonStyle.SOLID,
  iconRes: Int? = null,
) {
  val spec = LocalThemeSpec.current
  val (face, contentColor) =
    when (style) {
      BrButtonStyle.SOLID -> spec.accent to spec.onAccent
      BrButtonStyle.OUTLINE -> spec.background to spec.ink
      BrButtonStyle.INK -> spec.ink to spec.background
      BrButtonStyle.DANGER -> spec.background to spec.expense
    }
  val borderColor =
    when (style) {
      BrButtonStyle.OUTLINE -> spec.ink
      BrButtonStyle.DANGER -> spec.expense
      else -> face
    }
  val shadowColor = if (style == BrButtonStyle.DANGER) spec.expense else spec.border

  Box(modifier) {
    if (enabled) {
      Box(
        Modifier
          .matchParentSize()
          .offset(spec.shadowOffset, spec.shadowOffset)
          .background(shadowColor),
      )
    }
    Box(
      Modifier
        .fillMaxWidth()
        .background(if (enabled) face else spec.surfaceAlt)
        .border(spec.borderWidth, if (enabled) borderColor else spec.muted)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(PaddingValues(horizontal = 20.dp, vertical = 14.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (iconRes != null) {
          Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) contentColor else spec.muted,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = text.uppercase(),
          style = MaterialTheme.typography.labelLarge,
          color = if (enabled) contentColor else spec.muted,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
