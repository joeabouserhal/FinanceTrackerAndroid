package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

enum class BrButtonStyle { SOLID, OUTLINE, INK, DANGER }

/**
 * Direct brutalist button with a single bordered face and mono label.
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
  compact: Boolean = false,
  iconSize: androidx.compose.ui.unit.Dp = 18.dp,
  trailingIcon: Boolean = false,
  fillWidth: Boolean = true,
  trailingText: String? = null,
  /** Keep an asset's own colors instead of applying the theme tint. */
  preserveIconColors: Boolean = false,
  /** Explicit control height for alignment with adjacent fields/toggles. */
  minHeight: androidx.compose.ui.unit.Dp? = null,
) {
  val spec = LocalThemeSpec.current
  val (face, contentColor) =
    when (style) {
      BrButtonStyle.SOLID -> spec.accent to spec.onAccent
      BrButtonStyle.OUTLINE -> spec.surface to spec.ink
      BrButtonStyle.INK -> spec.ink to spec.background
      BrButtonStyle.DANGER -> spec.background to spec.expense
    }
  val borderColor =
    when (style) {
      BrButtonStyle.OUTLINE -> spec.border
      BrButtonStyle.DANGER -> spec.expense
      else -> face
    }
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()

  Box(modifier) {
    Box(
      Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .defaultMinSize(minHeight = minHeight ?: if (compact) 42.dp else 50.dp)
        .background(
          when {
            !enabled -> spec.surfaceAlt
            pressed && style == BrButtonStyle.OUTLINE -> spec.surfaceAlt
            pressed -> face.copy(alpha = 0.82f)
            else -> face
          },
        )
        .border(spec.borderWidth, if (enabled) borderColor else spec.muted)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = enabled,
          onClick = onClick,
        )
        .padding(
          PaddingValues(
            horizontal = if (compact) 13.dp else 18.dp,
            vertical = if (compact) 7.dp else 11.dp,
          ),
        ),
      contentAlignment = Alignment.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (iconRes != null && !trailingIcon) {
          if (preserveIconColors && enabled) {
            Image(
              painter = painterResource(iconRes),
              contentDescription = null,
              modifier = Modifier.size(iconSize),
            )
          } else {
            Icon(
              painter = painterResource(iconRes),
              contentDescription = null,
              tint = if (enabled) contentColor else spec.muted,
              modifier = Modifier.size(iconSize),
            )
          }
        }
        Text(
          text = text.uppercase(),
          style = MaterialTheme.typography.labelLarge,
          color = if (enabled) contentColor else spec.muted,
          fontWeight = FontWeight.Bold,
        )
        if (iconRes != null && trailingIcon) {
          if (preserveIconColors && enabled) {
            Image(
              painter = painterResource(iconRes),
              contentDescription = null,
              modifier = Modifier.size(iconSize),
            )
          } else {
            Icon(
              painter = painterResource(iconRes),
              contentDescription = null,
              tint = if (enabled) contentColor else spec.muted,
              modifier = Modifier.size(iconSize),
            )
          }
        }
        if (trailingText != null) {
          Text(
            text = trailingText,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else spec.muted,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}
