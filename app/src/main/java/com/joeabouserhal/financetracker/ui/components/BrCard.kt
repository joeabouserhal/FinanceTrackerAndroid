package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Major content block: clean face + offset hard shadow, no outline by default.
 * The face Box measures normally so the card always has real height; only the
 * shadow layer uses matchParentSize. Pass bordered = true for a visible frame.
 */
@Composable
fun BrCard(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  bordered: Boolean = false,
  content: @Composable BoxScope.() -> Unit,
) {
  val spec = LocalThemeSpec.current
  Box(modifier.padding(end = spec.shadowOffset, bottom = spec.shadowOffset)) {
    Box(
      Modifier
        .matchParentSize()
        .offset(spec.shadowOffset, spec.shadowOffset)
        .background(spec.border),
    )
    Box(
      Modifier
        .fillMaxWidth()
        .background(spec.surface)
        .then(if (bordered) Modifier.border(spec.borderWidth, spec.border) else Modifier)
        .padding(contentPadding),
    ) {
      content()
    }
  }
}

/** Flat panel; bordered by default (used for small info blocks). */
@Composable
fun BrPanel(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  bordered: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val spec = LocalThemeSpec.current
  Box(
    modifier
      .background(spec.surface)
      .then(if (bordered) Modifier.border(spec.borderWidth, spec.border) else Modifier)
      .padding(contentPadding),
  ) {
    content()
  }
}
