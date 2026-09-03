package com.joeabouserhal.financetracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import com.joeabouserhal.financetracker.data.settings.ThemeMode
import com.joeabouserhal.financetracker.data.settings.ThemeSelection

/**
 * Entry point used by the app: reads the persisted theme selection and
 * resolves it (system dark/light or an explicit custom spec) every time it
 * changes.
 */
@Composable
fun FinanceTrackerTheme(onReady: () -> Unit = {}, content: @Composable () -> Unit) {
  val app = LocalContext.current.applicationContext as FinanceTrackerApplication
  val selection by app.container.settingsRepository.themeSelection.collectAsStateWithLifecycle(
    initialValue = null,
  )
  if (selection != null) SideEffect { onReady() }
  FinanceTrackerTheme(spec = resolveSpec(selection ?: ThemeSelection()), content = content)
}

/** Preview/test-friendly overload: render a fixed spec directly. */
@Composable
fun FinanceTrackerTheme(spec: ThemeSpec, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalThemeSpec provides spec) {
    // Brutalist: every Material shape (dialogs, date picker, sheets, …) is
    // sharp-cornered instead of the default rounded corners.
    MaterialTheme(
      colorScheme = spec.toColorScheme(),
      typography = AppTypography,
      shapes =
        Shapes(
          extraSmall = RoundedCornerShape(0.dp),
          small = RoundedCornerShape(0.dp),
          medium = RoundedCornerShape(0.dp),
          large = RoundedCornerShape(0.dp),
          extraLarge = RoundedCornerShape(0.dp),
        ),
      content = content,
    )
  }
}

@Composable
fun resolveSpec(selection: ThemeSelection): ThemeSpec = resolveSpec(selection, isSystemInDarkTheme())

fun resolveSpec(selection: ThemeSelection, systemDark: Boolean): ThemeSpec =
  when (selection.mode) {
    ThemeMode.SYSTEM -> if (systemDark) ThemeCatalog.DarkBrutalist else ThemeCatalog.LightBrutalist
    ThemeMode.DARK -> ThemeCatalog.DarkBrutalist
    ThemeMode.LIGHT -> ThemeCatalog.LightBrutalist
    ThemeMode.CUSTOM -> ThemeCatalog.byId(selection.specId ?: ThemeCatalog.DarkBrutalist.id)
  }
