package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

@Composable
fun EmptyState(
  message: String,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  Column(
    modifier
      .background(spec.surfaceAlt)
      .padding(16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(message, style = MaterialTheme.typography.labelMedium, color = spec.muted)
  }
}
