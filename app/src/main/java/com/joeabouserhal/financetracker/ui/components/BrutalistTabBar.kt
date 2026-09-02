package com.joeabouserhal.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

data class TabItem(
  val label: String,
  @param:DrawableRes val iconRes: Int,
)

/**
 * Real bottom navbar: solid full-width surface anchored to the screen bottom,
 * a quiet top rule and an accent tick for the active destination.
 */
@Composable
fun BrutalistTabBar(
  tabs: List<TabItem>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  Column(
    modifier
      .fillMaxWidth()
      .background(spec.surface),
  ) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.65f)))

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars)
          .height(64.dp)
          .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEachIndexed { index, tab ->
        val selected = index == selectedIndex
        val tint by animateColorAsState(
          targetValue = if (selected) spec.accent else spec.muted,
          animationSpec = tween(180),
          label = "tabTint",
        )
        val indicatorWidth by animateDpAsState(
          targetValue = if (selected) 18.dp else 0.dp,
          animationSpec = tween(220),
          label = "tabIndicator",
        )
        Column(
          modifier =
            Modifier
              .weight(1f)
              .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
              .padding(horizontal = 2.dp, vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
          )
          Text(
            text = tab.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
          Box(
            Modifier
              .width(indicatorWidth)
              .height(2.dp)
              .background(if (selected) spec.accent else Color.Transparent),
          )
        }
      }
    }
  }
}
