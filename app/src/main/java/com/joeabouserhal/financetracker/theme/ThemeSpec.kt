package com.joeabouserhal.financetracker.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
  /** Gold/yellow semantic color for goal-completion transactions. */
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
      primaryContainer = surfaceAlt,
      onPrimaryContainer = ink,
      secondary = accent,
      onSecondary = onAccent,
      secondaryContainer = surfaceAlt,
      onSecondaryContainer = ink,
      tertiary = goal,
      onTertiary = readableOn(goal),
      tertiaryContainer = surfaceAlt,
      onTertiaryContainer = ink,
      background = background,
      onBackground = ink,
      surface = surface,
      onSurface = ink,
      surfaceVariant = surfaceAlt,
      onSurfaceVariant = muted,
      outline = border,
      outlineVariant = border.copy(alpha = 0.5f),
      surfaceTint = accent,
      surfaceDim = background,
      surfaceBright = surfaceAlt,
      surfaceContainerLowest = background,
      surfaceContainerLow = surface,
      surfaceContainer = surface,
      surfaceContainerHigh = surfaceAlt,
      surfaceContainerHighest = surfaceAlt,
      inverseSurface = ink,
      inverseOnSurface = background,
      inversePrimary = onAccent,
      error = expense,
      onError = readableOn(expense),
      errorContainer = surfaceAlt,
      onErrorContainer = expense,
    )
  }
}

/** Filled semantic controls must remain readable in both light and dark palettes. */
private fun readableOn(color: Color): Color =
  if (color.luminance() > 0.179f) Color.Black else Color.White

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

  // Community palette adaptations; sources and MIT notices ship in
  // res/raw/theme_licenses.txt. Geometry remains the app's modern brutalist style.
  val OneDark = ThemeSpec(
    id = "one_dark", name = "One Dark", isDark = true,
    background = Color(0xFF282C34), surface = Color(0xFF2C313A), surfaceAlt = Color(0xFF353B45),
    ink = Color(0xFFABB2BF), muted = Color(0xFFA0A8B7), border = Color(0xFF5C6370),
    accent = Color(0xFF61AFEF), onAccent = Color(0xFF282C34),
    income = Color(0xFF98C379), expense = Color(0xFFE88991), goal = Color(0xFFE5C07B),
  )

  val Dracula = ThemeSpec(
    id = "dracula", name = "Dracula", isDark = true,
    background = Color(0xFF282A36), surface = Color(0xFF303341), surfaceAlt = Color(0xFF383B4C),
    ink = Color(0xFFF8F8F2), muted = Color(0xFFABB2CF), border = Color(0xFF6272A4),
    accent = Color(0xFFBD93F9), onAccent = Color(0xFF282A36),
    income = Color(0xFF50FA7B), expense = Color(0xFFFF8585), goal = Color(0xFFF1FA8C),
  )

  val CatppuccinMocha = ThemeSpec(
    id = "catppuccin_mocha", name = "Catppuccin Mocha", isDark = true,
    background = Color(0xFF1E1E2E), surface = Color(0xFF181825), surfaceAlt = Color(0xFF313244),
    ink = Color(0xFFCDD6F4), muted = Color(0xFFBAC2DE), border = Color(0xFF6C7086),
    accent = Color(0xFFCBA6F7), onAccent = Color(0xFF1E1E2E),
    income = Color(0xFFA6E3A1), expense = Color(0xFFF38BA8), goal = Color(0xFFF9E2AF),
  )

  val CatppuccinMacchiato = ThemeSpec(
    id = "catppuccin_macchiato", name = "Catppuccin Macchiato", isDark = true,
    background = Color(0xFF24273A), surface = Color(0xFF1E2030), surfaceAlt = Color(0xFF363A4F),
    ink = Color(0xFFCAD3F5), muted = Color(0xFFB8C0E0), border = Color(0xFF6E738D),
    accent = Color(0xFFC6A0F6), onAccent = Color(0xFF24273A),
    income = Color(0xFFA6DA95), expense = Color(0xFFED8796), goal = Color(0xFFEED49F),
  )

  val CatppuccinFrappe = ThemeSpec(
    id = "catppuccin_frappe", name = "Catppuccin Frappé", isDark = true,
    background = Color(0xFF303446), surface = Color(0xFF292C3C), surfaceAlt = Color(0xFF393D50),
    ink = Color(0xFFC6D0F5), muted = Color(0xFFB5BFE2), border = Color(0xFF737994),
    accent = Color(0xFFCA9EE6), onAccent = Color(0xFF303446),
    income = Color(0xFFA6D189), expense = Color(0xFFF0999B), goal = Color(0xFFE5C890),
  )

  val CatppuccinLatte = ThemeSpec(
    id = "catppuccin_latte", name = "Catppuccin Latte", isDark = false,
    background = Color(0xFFEFF1F5), surface = Color(0xFFF8F9FC), surfaceAlt = Color(0xFFE8EBF1),
    ink = Color(0xFF4C4F69), muted = Color(0xFF5C5F77), border = Color(0xFF8C8FA1),
    accent = Color(0xFF8839EF), onAccent = Color(0xFFFFFFFF),
    income = Color(0xFF287A1B), expense = Color(0xFFD20F39), goal = Color(0xFF906014),
  )

  val original = listOf(DarkBrutalist, LightBrutalist, AcidPunk, HighContrast)
  val community = listOf(OneDark, Dracula, CatppuccinMocha, CatppuccinMacchiato, CatppuccinFrappe, CatppuccinLatte)
  val all = original + community

  fun byId(id: String): ThemeSpec = all.firstOrNull { it.id == id } ?: DarkBrutalist
}

/** Composition local so components can read border widths, income/expense colors, etc. */
val LocalThemeSpec = staticCompositionLocalOf { ThemeCatalog.DarkBrutalist }
