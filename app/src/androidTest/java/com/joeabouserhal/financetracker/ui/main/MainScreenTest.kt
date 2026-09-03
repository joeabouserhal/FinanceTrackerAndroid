package com.joeabouserhal.financetracker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.ThemeCatalog
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.joeabouserhal.financetracker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { FinanceTrackerTheme(ThemeCatalog.DarkBrutalist) { MainScreen(onItemClick = {}) } }
  }

  @Test
  fun everyThemeAppearsInTheComponentShowcase() {
    ThemeCatalog.all.forEach { composeTestRule.onNodeWithText(it.name.uppercase()).performScrollTo().assertIsDisplayed() }
  }
}
