package com.joeabouserhal.financetracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.settings.ThemeSelection
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import com.joeabouserhal.financetracker.theme.resolveSpec
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class ThemesScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun allPalettesCanBeSelectedAtNarrowWidthWithLargeText() {
    val selection = mutableStateOf(ThemeSelection())
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
        FinanceTrackerTheme(resolveSpec(selection.value, systemDark = false)) {
          Box(Modifier.width(320.dp)) {
            ThemesContent(selection.value, onSelect = { selection.value = it }, onBack = {})
          }
        }
      }
    }
    ThemeCatalog.community.forEach { theme ->
      compose.onNodeWithText(theme.name).performScrollTo().performClick().assertIsSelected().assertIsDisplayed()
      repeat(3) { index ->
        compose.onNodeWithTag("theme-swatch:${theme.name}:$index", useUnmergedTree = true)
          .assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
      }
      val card = compose.onNodeWithText(theme.name).fetchSemanticsNode().boundsInRoot
      val swatches = compose.onNodeWithTag("theme-swatches:${theme.name}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
      assertEquals("Swatches sit at the card's right padding", with(compose.density) { 12.dp.toPx() }, card.right - swatches.right, 1f)
    }
    compose.onNodeWithText("THEME CREDITS & LICENSES").performScrollTo().performClick()
    compose.onNodeWithText("THEME CREDITS").assertIsDisplayed()
    compose.onNodeWithText("CLOSE").performClick()
    compose.onNodeWithText("Follow system").performScrollTo().performClick().assertIsSelected()
  }
}
