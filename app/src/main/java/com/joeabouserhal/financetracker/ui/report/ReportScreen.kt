package com.joeabouserhal.financetracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.EmptyState
import com.joeabouserhal.financetracker.ui.components.ScreenHeader

/** WIP placeholder — reports land here in a later phase. */
@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
  val spec = LocalThemeSpec.current
  Column(modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Report", subtitle = "COMING SOON")
    EmptyState(
      message = "Reports are a work in progress — check back soon.",
      modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
  }
}
