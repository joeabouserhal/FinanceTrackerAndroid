package com.joeabouserhal.financetracker.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.ui.components.BrutalistTabBar
import com.joeabouserhal.financetracker.ui.components.TabItem
import com.joeabouserhal.financetracker.ui.dashboard.DashboardScreen
import com.joeabouserhal.financetracker.ui.presets.PresetsScreen
import com.joeabouserhal.financetracker.ui.report.ReportScreen
import com.joeabouserhal.financetracker.ui.settings.OptionsScreen
import com.joeabouserhal.financetracker.ui.transactions.TransactionsScreen

enum class MainTab(val label: String) {
  DASHBOARD("Dashboard"),
  TRANSACTIONS("Transactions"),
  PRESETS("Presets"),
  REPORT("Report"),
  OPTIONS("Options"),
}

private val TAB_ITEMS =
  listOf(
    TabItem("Dashboard", R.drawable.ic_tab_dashboard),
    TabItem("Transactions", R.drawable.ic_tab_transactions),
    TabItem("Presets", R.drawable.ic_tab_presets),
    TabItem("Report", R.drawable.ic_tab_report),
    TabItem("Options", R.drawable.ic_tab_settings),
  )

/**
 * Tab host. Content cross-fades between tabs; the real bottom navbar sits at
 * the screen bottom and handles the system navigation-bar inset itself.
 */
@Composable
fun MainTabs(
  onAddTransaction: () -> Unit,
  onEditTransaction: (String) -> Unit,
  onOpenCurrenciesAccounts: () -> Unit,
  onOpenThemes: () -> Unit,
  onOpenCategories: () -> Unit,
  onOpenAuth: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }

  Column(modifier.fillMaxSize()) {
    Box(
      Modifier
        .weight(1f)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
      AnimatedContent(
        targetState = selectedTab,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
        label = "tabContent",
      ) { tab ->
        when (tab) {
          MainTab.DASHBOARD ->
            DashboardScreen(
              onAddTransaction = onAddTransaction,
              onEditTransaction = onEditTransaction,
              onSeeAllTransactions = { selectedTab = MainTab.TRANSACTIONS },
            )
          MainTab.TRANSACTIONS -> TransactionsScreen(
            onAddTransaction = onAddTransaction,
            onEditTransaction = onEditTransaction,
          )
          MainTab.PRESETS -> PresetsScreen()
          MainTab.REPORT -> ReportScreen()
          MainTab.OPTIONS -> OptionsScreen(
            onOpenCurrenciesAccounts = onOpenCurrenciesAccounts,
            onOpenThemes = onOpenThemes,
            onOpenCategories = onOpenCategories,
            onSignIn = onOpenAuth,
          )
        }
      }
    }
    BrutalistTabBar(
      tabs = TAB_ITEMS,
      selectedIndex = selectedTab.ordinal,
      onSelect = { selectedTab = MainTab.entries[it] },
    )
  }
}
