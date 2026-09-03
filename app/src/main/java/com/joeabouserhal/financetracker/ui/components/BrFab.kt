package com.joeabouserhal.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Labeled floating trigger for the add-action menu. */
@Composable
fun BrFab(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  expanded: Boolean = false,
  contentDescription: String = "Open add menu",
) {
  val spec = LocalThemeSpec.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.97f else 1f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 650f),
    label = "fabPress",
  )
  val face = spec.accent
  val contentColor = spec.onAccent

  Row(
    modifier =
      modifier
        .scale(scale)
        .height(54.dp)
        .width(104.dp)
        .background(face)
        .border(spec.borderWidth, spec.onAccent.copy(alpha = 0.72f))
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        )
        .padding(horizontal = 13.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = if (expanded) "CLOSE" else "ADD",
      style = MaterialTheme.typography.labelLarge,
      color = contentColor,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.width(48.dp),
    )
    Icon(
      painter = painterResource(if (expanded) R.drawable.ic_close else R.drawable.ic_add),
      contentDescription = contentDescription,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
  }
}
