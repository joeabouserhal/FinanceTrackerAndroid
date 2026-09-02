package com.joeabouserhal.financetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Full-width segmented toggle drawn as ONE element: a single outer border,
 * no inner seams, and the selected half's fill + text color animate on
 * switch. Optional [optionColors] gives each option its own accent (e.g.
 * expense red / income green).
 */
@Composable
fun BrSegmentedToggle(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  optionColors: List<Color>? = null,
) {
  val spec = LocalThemeSpec.current
  Row(
    modifier
      .fillMaxWidth()
      .background(spec.surface)
      .border(spec.borderWidth, spec.border),
  ) {
    options.forEachIndexed { index, label ->
      val selected = index == selectedIndex
      val accent = optionColors?.getOrNull(index) ?: spec.accent
      val bg by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = tween(180),
        label = "segmentBg",
      )
      val fg by animateColorAsState(
        targetValue =
          if (selected) {
            if (accent.luminance() < 0.5f) Color.White else Color.Black
          } else {
            spec.muted
          },
        animationSpec = tween(180),
        label = "segmentFg",
      )
      Box(
        Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .background(bg)
          .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
          .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label.uppercase(),
          style = MaterialTheme.typography.labelLarge,
          color = fg,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
