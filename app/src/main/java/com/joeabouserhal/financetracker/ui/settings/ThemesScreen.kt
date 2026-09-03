package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.settings.ThemeMode
import com.joeabouserhal.financetracker.data.settings.ThemeSelection
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.theme.ThemeSpec
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

private data class ThemeOption(
  val label: String,
  val selection: ThemeSelection,
  val swatches: List<androidx.compose.ui.graphics.Color>,
  val subtitle: String,
)

private fun ThemeSpec.option() = ThemeOption(
  name,
  ThemeSelection(
    when (id) {
      ThemeCatalog.DarkBrutalist.id -> ThemeMode.DARK
      ThemeCatalog.LightBrutalist.id -> ThemeMode.LIGHT
      else -> ThemeMode.CUSTOM
    },
    if (this in listOf(ThemeCatalog.DarkBrutalist, ThemeCatalog.LightBrutalist)) null else id,
  ),
  listOf(background, accent, income),
  if (isDark) "Dark palette" else "Light palette",
)

@Composable
fun ThemesScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val selection by container.settingsRepository.themeSelection.collectAsStateWithLifecycle(initialValue = ThemeSelection())
  ThemesContent(
    selection = selection,
    onSelect = { scope.launch { container.settingsRepository.setThemeSelection(it) } },
    onBack = onBack,
    modifier = modifier,
  )
}

/** Stateless picker lets previews and device tests exercise themes without changing user settings. */
@Composable
internal fun ThemesContent(
  selection: ThemeSelection,
  onSelect: (ThemeSelection) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  var showCredits by rememberSaveable { mutableStateOf(false) }

  val groups = listOf(
    "ORIGINAL" to (
      listOf(ThemeOption("Follow system", ThemeSelection(ThemeMode.SYSTEM), listOf(ThemeCatalog.DarkBrutalist.background, ThemeCatalog.LightBrutalist.background, ThemeCatalog.DarkBrutalist.accent), "Automatic light / dark")) +
        ThemeCatalog.original.map { it.option() }
      ),
    "OPEN SOURCE" to ThemeCatalog.community.map { it.option() },
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
        .selectableGroup()
        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      groups.forEach { (heading, options) ->
        Text(heading, style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        options.forEach { option ->
          ThemeOptionRow(option, selected = option.selection == selection, onClick = { onSelect(option.selection) })
        }
      }
      Text("Applies instantly. Saved on this device, available offline.", style = MaterialTheme.typography.bodySmall, color = spec.muted, modifier = Modifier.padding(top = 12.dp))
      Text("THEME CREDITS & LICENSES", style = MaterialTheme.typography.labelMedium, color = spec.accent, modifier = Modifier.fillMaxWidth().clickable { showCredits = true }.padding(vertical = 16.dp))
    }
  }

  if (showCredits) {
    val context = LocalContext.current
    val credits = remember(context) { context.resources.openRawResource(R.raw.theme_licenses).bufferedReader().use { it.readText() } }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    BrDialog(title = "Theme credits", onDismiss = { showCredits = false }, dismissText = "CLOSE") {
      Text(credits, style = MaterialTheme.typography.bodySmall, color = spec.ink, modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()))
    }
  }
}

@Composable
private fun ThemeOptionRow(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  val rowColor by animateColorAsState(
    targetValue = if (selected) spec.surfaceAlt else spec.surface,
    animationSpec = tween(160),
    label = "themeRow",
  )
  Row(
    Modifier
      .fillMaxWidth()
      .background(rowColor)
      .border(1.dp, if (selected) spec.accent else spec.border.copy(alpha = 0.4f))
      .heightIn(min = 64.dp)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(option.label, style = MaterialTheme.typography.bodyMedium, color = spec.ink)
      Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
    Box(Modifier.padding(end = 10.dp).size(18.dp), contentAlignment = Alignment.Center) {
      Text(if (selected) "✓" else "", style = MaterialTheme.typography.labelLarge, color = spec.accent)
    }
    Row(Modifier.testTag("theme-swatches:${option.label}").border(1.dp, spec.border.copy(alpha = 0.45f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      option.swatches.forEachIndexed { index, color ->
        Box(Modifier.size(18.dp).background(color).testTag("theme-swatch:${option.label}:$index"))
      }
    }
  }
}
