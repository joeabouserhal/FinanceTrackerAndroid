package com.joeabouserhal.financetracker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.theme.ThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrCard
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.OfflineIndicator
import com.joeabouserhal.financetracker.ui.components.ScreenHeader

/**
 * Phase 1 showcase: exercises every brutalist base component and the four
 * built-in theme specs. This screen will be replaced by the real dashboard in
 * Phase 3.
 */
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedChip by remember { mutableIntStateOf(0) }
  var sampleText by remember { mutableStateOf("") }

  Column(
    modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState()),
  ) {
    ScreenHeader(title = "FINANCE TRACKER", subtitle = "BRUTALIST UI PREVIEW — PHASE 1")

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OfflineIndicator(isOffline = true)
        Spacer(Modifier.size(8.dp))
        OfflineIndicator(isOffline = false)
      }

      BrButton(text = "Add transaction", onClick = {})
      BrButton(text = "Outline button", onClick = {}, style = BrButtonStyle.OUTLINE)

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Income", "Expense").forEachIndexed { index, label ->
          BrChip(
            text = label,
            selected = selectedChip == index,
            onClick = { selectedChip = index },
            colorDot = if (index > 0) MaterialTheme.colorScheme.primary else null,
          )
        }
      }

      BrTextField(
        value = sampleText,
        onValueChange = { sampleText = it },
        label = "SAMPLE FIELD",
        modifier = Modifier.fillMaxWidth(),
      )

      BrCard {
        Column {
          Text("BALANCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            "\u00A0+$1,240.50",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text("USD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      Text("THEME SPECS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeCatalog.all.forEach { spec ->
          ThemeSwatch(spec)
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ThemeSwatch(spec: ThemeSpec) {
  Column(
    Modifier
      .fillMaxWidth()
      .border(spec.borderWidth, spec.border)
      .background(spec.background)
      .padding(12.dp),
  ) {
    Text(spec.name.uppercase(), style = MaterialTheme.typography.labelLarge, color = spec.ink)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(spec.accent, spec.income, spec.expense, spec.ink, spec.surface).forEach { color ->
        androidx.compose.foundation.layout.Box(Modifier.size(24.dp).background(color).border(1.dp, spec.border))
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenDarkPreview() {
  FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) { MainScreen(onItemClick = {}) }
}

@Preview(showBackground = true)
@Composable
fun MainScreenLightPreview() {
  FinanceTrackerTheme(ThemeCatalog.LightBrutalist) { MainScreen(onItemClick = {}) }
}
