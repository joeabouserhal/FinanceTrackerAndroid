package com.joeabouserhal.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Compact menu anchored above a fixed-size add button. */
@Composable
fun ExpandableFab(
  onAddTransaction: () -> Unit,
  onAddFromPreset: () -> Unit,
  modifier: Modifier = Modifier,
  onTransfer: () -> Unit = {},
) {
  var expanded by remember { mutableStateOf(false) }
  val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
  val position = remember(gap) {
    object : PopupPositionProvider {
      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
      ): IntOffset = IntOffset(
        (anchorBounds.right - popupContentSize.width).coerceIn(gap, maxOf(gap, windowSize.width - popupContentSize.width - gap)),
        (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap),
      )
    }
  }

  Box(modifier) {
    BrFab(
      onClick = { expanded = !expanded },
      expanded = expanded,
      contentDescription = if (expanded) "Close add menu" else "Open add menu",
    )
    if (expanded) {
      Popup(
        popupPositionProvider = position,
        onDismissRequest = { expanded = false },
        properties = PopupProperties(focusable = true),
      ) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
        Column(
          Modifier.width(208.dp).graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * gap
          }.background(LocalThemeSpec.current.accent),
        ) {
          ActionRow(R.drawable.ic_transfer, "TRANSFER") { expanded = false; onTransfer() }
          MenuDivider()
          ActionRow(R.drawable.ic_tab_presets, "FROM PRESET") { expanded = false; onAddFromPreset() }
          MenuDivider()
          ActionRow(R.drawable.ic_tab_transactions, "TRANSACTION") { expanded = false; onAddTransaction() }
        }
      }
    }
  }
}

@Composable
private fun MenuDivider() {
  val spec = LocalThemeSpec.current
  Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(spec.onAccent.copy(alpha = 0.16f)))
}

@Composable
private fun ActionRow(@DrawableRes iconRes: Int, title: String, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Row(
    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(painterResource(iconRes), contentDescription = null, tint = spec.onAccent, modifier = Modifier.size(20.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = spec.onAccent, modifier = Modifier.weight(1f))
  }
}
