package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Compact editorial page heading. Hierarchy comes from type and whitespace. */
@Composable
fun ScreenHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  val spec = LocalThemeSpec.current
  Column(
    modifier
      .fillMaxWidth()
      .background(spec.background)
      .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
  ) {
    Text(
      title.uppercase(),
      style = MaterialTheme.typography.headlineLarge,
      color = spec.ink,
    )
    if (subtitle != null) {
      Text(
        subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
        modifier = Modifier.padding(top = 5.dp),
      )
    }
  }
}
