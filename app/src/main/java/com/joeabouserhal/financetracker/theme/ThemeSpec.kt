package com.joeabouserhal.financetracker.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A pluggable theme spec: every brutalist theme is just a data class with the
 * same slots, so new themes are added to [ThemeCatalog] without touching any
 * component code.
 */
@Immutable
data class ThemeSpec(
  val id: String,
  val name: String,
  val isDark: Boolean,
  val background: Color,
  val surface: Color,
  val surfaceAlt: Color,
  val ink: Color,
  val muted: Color,
  val border: Color,
  val accent: Color,
  val onAccent: Color,
  val income: Color,
  val expense: Color,
  /** Custom "goal completion" transaction color — metallic gold in every theme. */
  val goal: Color = Color(0xFFD4AF37),
  val borderWidth: Dp = 1.dp,
  val shadowOffset: Dp = 2.dp,
  val cornerRadius: Dp = 0.dp,
) {
  fun toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
      primary = accent,
      onPrimary = onAccent,
      background = background,
      onBackground = ink,
      surface = surface,
      onSurface = ink,
      surfaceVariant = surfaceAlt,
      onSurfaceVariant = muted,
      outline = border,
      error = expense,
      onError = ink,
    )
  }
}

/** All selectable themes. Add new brutalist variants here. */
object ThemeCatalog {
  val DarkBrutalist =
    ThemeSpec(
      id = "dark_brutalist",
      name = "Dark Brutalist",
      isDark = true,
      background = Color(0xFF0C0C0B),
      surface = Color(0xFF151514),
      surfaceAlt = Color(0xFF20201E),
      ink = Color(0xFFF1EFE7),
      muted = Color(0xFF969188),
      border = Color(0xFF56534C),
      accent = Color(0xFFD1F34A),
      onAccent = Color(0xFF10100F),
      income = Color(0xFF3ECF8E),
      expense = Color(0xFFFF4D4D),
    )

  val LightBrutalist =
    ThemeSpec(
      id = "light_brutalist",
      name = "Light Brutalist",
      isDark = false,
      background = Color(0xFFF4F1E8),
      surface = Color(0xFFFFFFFF),
      surfaceAlt = Color(0xFFE7E3D6),
      ink = Color(0xFF0A0A0A),
      muted = Color(0xFF6B675F),
      border = Color(0xFF706C64),
      accent = Color(0xFF7C3AED),
      onAccent = Color(0xFFFFFFFF),
      income = Color(0xFF0B7A43),
      expense = Color(0xFFC62828),
    )

  val AcidPunk =
    ThemeSpec(
      id = "acid_punk",
      name = "Acid Punk",
      isDark = true,
      background = Color(0xFF12001F),
      surface = Color(0xFF1E0033),
      surfaceAlt = Color(0xFF2A0A44),
      ink = Color(0xFFF3E9FF),
      muted = Color(0xFFBCA8D6),
      border = Color(0xFF775D88),
      accent = Color(0xFFFF2FD6),
      onAccent = Color(0xFF12001F),
      income = Color(0xFF00F0FF),
      expense = Color(0xFFFF5C39),
    )

  val HighContrast =
    ThemeSpec(
      id = "high_contrast",
      name = "High Contrast",
      isDark = true,
      background = Color(0xFF000000),
      surface = Color(0xFF000000),
      surfaceAlt = Color(0xFF111111),
      ink = Color(0xFFFFFFFF),
      muted = Color(0xFFC8C8C8),
      border = Color(0xFFFFFFFF),
      accent = Color(0xFFFFFF00),
      onAccent = Color(0xFF000000),
      income = Color(0xFF00FF00),
      expense = Color(0xFFFF0000),
    )

  val all = listOf(DarkBrutalist, LightBrutalist, AcidPunk, HighContrast)

  fun byId(id: String): ThemeSpec = all.firstOrNull { it.id == id } ?: DarkBrutalist
}

/** Composition local so components can read border widths, income/expense colors, etc. */
val LocalThemeSpec = staticCompositionLocalOf { ThemeCatalog.DarkBrutalist }
