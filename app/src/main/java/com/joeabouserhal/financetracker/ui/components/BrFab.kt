package com.joeabouserhal.financetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Compact square action: accent face, restrained hard shadow, press spring.
 */
@Composable
fun BrFab(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  contentDescription: String = "Add transaction",
  showCloseIcon: Boolean = false,
) {
  val spec = LocalThemeSpec.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.9f else 1f,
    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    label = "fabScale",
  )

  Box(modifier.size(58.dp)) {
    Box(
      Modifier
        .size(54.dp)
        .offset(spec.shadowOffset, spec.shadowOffset)
        .background(spec.muted),
    )
    Box(
      Modifier
        .size(54.dp)
        .scale(scale)
        .background(spec.accent)
        .border(spec.borderWidth, spec.onAccent)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        ),
      contentAlignment = Alignment.Center,
    ) {
      if (showCloseIcon) {
        Text("×", color = spec.onAccent, fontSize = 24.sp)
      } else {
        Icon(
          painter = painterResource(R.drawable.ic_add),
          contentDescription = contentDescription,
          tint = spec.onAccent,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}
