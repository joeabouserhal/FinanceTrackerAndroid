package com.joeabouserhal.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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
  val rotation by animateFloatAsState(
    targetValue = if (expanded) 45f else 0f,
    label = "fabExpansion",
  )

  Row(
    modifier =
      modifier
        .scale(scale)
        .height(54.dp)
        .width(104.dp)
        .background(face)
        .semantics {
          this.contentDescription = contentDescription
          stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
        .clickable(
          role = Role.Button,
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
      painter = painterResource(R.drawable.ic_add),
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp).rotate(rotation),
    )
  }
}
