package com.joeabouserhal.financetracker.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }
  }
}
