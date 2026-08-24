package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brutalist hard-shadow layer: a solid offset block drawn behind the content.
 * The content must draw its own opaque background on top (see BrButton/BrCard).
 */
fun Modifier.hardShadow(
  color: Color,
  offset: Dp = 3.dp,
  cornerRadius: Dp = 0.dp,
): Modifier =
  drawBehind {
    val o = offset.toPx()
    val corner = cornerRadius.toPx()
    drawRoundRect(
      color = color,
      topLeft = Offset(o, o),
      size = Size(size.width - o, size.height - o),
      cornerRadius = CornerRadius(corner, corner),
    )
  }

/** Two-box brutalist button/card construction: shadow block + bordered face. */
@Composable
fun BrutalistSurface(
  shadowColor: Color,
  faceColor: Color,
  borderColor: Color,
  modifier: Modifier = Modifier,
  borderWidth: Dp = 2.dp,
  cornerRadius: Dp = 0.dp,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier) {
    Box(
      Modifier
        .matchParentSize()
        .offset(4.dp, 4.dp)
        .background(shadowColor),
    )
    Box(
      Modifier
        .matchParentSize()
        .background(faceColor),
    ) {
      content()
    }
  }
}
