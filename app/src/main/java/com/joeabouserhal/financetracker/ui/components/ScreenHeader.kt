package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Big Archivo Black page title — intentionally borderless. */
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
      .padding(horizontal = 16.dp, vertical = 24.dp),
  ) {
    Text(title, style = MaterialTheme.typography.headlineLarge, color = spec.ink)
    if (subtitle != null) {
      Text(
        subtitle,
        style = MaterialTheme.typography.labelMedium,
        color = spec.muted,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}
