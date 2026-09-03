package com.joeabouserhal.financetracker.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.settings.ThemeMode
import com.joeabouserhal.financetracker.data.settings.ThemeSelection

class ThemeCatalogTest {
  @Test
  fun `all built-in themes have unique ids`() {
    val ids = ThemeCatalog.all.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    assertTrue(ThemeCatalog.all.size >= 4)
  }

  @Test
  fun `byId returns exact match`() {
    assertSame(ThemeCatalog.DarkBrutalist, ThemeCatalog.byId("dark_brutalist"))
    assertSame(ThemeCatalog.LightBrutalist, ThemeCatalog.byId("light_brutalist"))
  }

  @Test
  fun `byId falls back to DarkBrutalist for unknown id`() {
    assertSame(ThemeCatalog.DarkBrutalist, ThemeCatalog.byId("does_not_exist"))
  }

  @Test
  fun `color scheme maps accent and background from spec`() {
    ThemeCatalog.all.forEach { spec ->
      val scheme = spec.toColorScheme()
      assertEquals(spec.accent, scheme.primary)
      assertEquals(spec.background, scheme.background)
      assertEquals(spec.ink, scheme.onBackground)
      assertEquals(spec.accent, scheme.secondary)
      assertEquals(spec.surface, scheme.surfaceContainer)
      assertEquals(spec.surfaceAlt, scheme.surfaceContainerHigh)
      assertEquals(spec.ink, scheme.onPrimaryContainer)
    }
  }

  @Test
  fun `all six community palettes are selectable without changing geometry`() {
    assertEquals(setOf("one_dark", "dracula", "catppuccin_mocha", "catppuccin_macchiato", "catppuccin_frappe", "catppuccin_latte"), ThemeCatalog.community.map { it.id }.toSet())
    ThemeCatalog.community.forEach { spec ->
      assertSame(spec, ThemeCatalog.byId(spec.id))
      assertEquals(1.dp, spec.borderWidth)
      assertEquals(0.dp, spec.cornerRadius)
      listOf(true, false).forEach { systemDark ->
        assertSame(spec, resolveSpec(ThemeSelection(ThemeMode.CUSTOM, spec.id), systemDark))
      }
    }
  }

  @Test
  fun `existing modes and unknown selections retain their fallback`() {
    assertSame(ThemeCatalog.DarkBrutalist, resolveSpec(ThemeSelection(), true))
    assertSame(ThemeCatalog.LightBrutalist, resolveSpec(ThemeSelection(), false))
    assertSame(ThemeCatalog.DarkBrutalist, resolveSpec(ThemeSelection(ThemeMode.DARK), false))
    assertSame(ThemeCatalog.LightBrutalist, resolveSpec(ThemeSelection(ThemeMode.LIGHT), true))
    assertSame(ThemeCatalog.DarkBrutalist, resolveSpec(ThemeSelection(ThemeMode.CUSTOM, "missing"), false))
  }

  @Test
  fun `community text and action colors retain readable contrast`() {
    ThemeCatalog.community.forEach { spec ->
      listOf(spec.background, spec.surface, spec.surfaceAlt).forEach { surface ->
        mapOf("text" to spec.ink, "muted" to spec.muted, "accent" to spec.accent, "income" to spec.income, "expense" to spec.expense, "goal" to spec.goal).forEach { (role, foreground) ->
          assertTrue("${spec.name} $role contrast: ${contrast(foreground, surface)}", contrast(foreground, surface) >= 4.5f)
        }
      }
      assertTrue("${spec.name} button label", contrast(spec.onAccent, spec.accent) >= 4.5f)
      val scheme = spec.toColorScheme()
      assertTrue("${spec.name} error label", contrast(scheme.onError, scheme.error) >= 4.5f)
    }
  }

  private fun contrast(a: Color, b: Color): Float =
    (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
