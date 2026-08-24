package com.joeabouserhal.financetracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
fun FinanceTrackerTheme(content: @Composable () -> Unit) {
  val app = LocalContext.current.applicationContext as FinanceTrackerApplication
  val selection by app.container.settingsRepository.themeSelection.collectAsStateWithLifecycle(
    initialValue = ThemeSelection(),
  )
  FinanceTrackerTheme(spec = resolveSpec(selection), content = content)
}

/** Preview/test-friendly overload: render a fixed spec directly. */
@Composable
fun FinanceTrackerTheme(spec: ThemeSpec, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalThemeSpec provides spec) {
    MaterialTheme(colorScheme = spec.toColorScheme(), typography = AppTypography, content = content)
  }
}

@Composable
fun resolveSpec(selection: ThemeSelection): ThemeSpec =
  when (selection.mode) {
    ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) ThemeCatalog.DarkBrutalist else ThemeCatalog.LightBrutalist
    ThemeMode.DARK -> ThemeCatalog.DarkBrutalist
    ThemeMode.LIGHT -> ThemeCatalog.LightBrutalist
    ThemeMode.CUSTOM -> ThemeCatalog.byId(selection.specId ?: ThemeCatalog.DarkBrutalist.id)
  }
