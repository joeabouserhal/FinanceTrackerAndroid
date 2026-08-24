package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.settings.ThemeMode
import com.joeabouserhal.financetracker.data.settings.ThemeSelection
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

private data class ThemeOption(
  val label: String,
  val selection: ThemeSelection,
  val swatches: List<androidx.compose.ui.graphics.Color>,
)

@Composable
fun ThemesScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val selection by container.settingsRepository.themeSelection.collectAsStateWithLifecycle(initialValue = ThemeSelection())

  val options =
    listOf(
      ThemeOption("Follow system", ThemeSelection(ThemeMode.SYSTEM), listOf(spec.background, spec.ink)),
      ThemeOption("Dark Brutalist", ThemeSelection(ThemeMode.DARK), listOf(ThemeCatalog.DarkBrutalist.accent, ThemeCatalog.DarkBrutalist.ink)),
      ThemeOption("Light Brutalist", ThemeSelection(ThemeMode.LIGHT), listOf(ThemeCatalog.LightBrutalist.accent, ThemeCatalog.LightBrutalist.ink)),
      ThemeOption("Acid Punk", ThemeSelection(ThemeMode.CUSTOM, "acid_punk"), listOf(ThemeCatalog.AcidPunk.accent, ThemeCatalog.AcidPunk.income)),
      ThemeOption("High Contrast", ThemeSelection(ThemeMode.CUSTOM, "high_contrast"), listOf(ThemeCatalog.HighContrast.accent, ThemeCatalog.HighContrast.ink)),
    )

  Column(modifier.fillMaxSize().background(spec.background)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("< BACK", style = MaterialTheme.typography.labelMedium, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack))
    }
    ScreenHeader(title = "Themes", subtitle = "PICK YOUR LOOK")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      options.forEach { option ->
        val selected = option.selection == selection
        val rowColor by animateColorAsState(
          targetValue = if (selected) spec.surfaceAlt else spec.surface,
          animationSpec = tween(160),
          label = "themeRow",
        )
        Row(
          Modifier
            .fillMaxWidth()
            .background(rowColor)
            .minimumInteractiveComponentSize()
            .clickable { scope.launch { container.settingsRepository.setThemeSelection(option.selection) } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            option.swatches.forEach { color -> Box(Modifier.size(16.dp).background(color)) }
          }
          Text(option.label, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
          Text(if (selected) "✓" else "", style = MaterialTheme.typography.labelLarge, color = spec.accent)
        }
      }
    }
  }
}
