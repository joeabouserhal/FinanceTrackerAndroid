package com.joeabouserhal.financetracker

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.joeabouserhal.financetracker.ui.auth.AuthScreen
import com.joeabouserhal.financetracker.ui.categories.CategoriesScreen
import com.joeabouserhal.financetracker.ui.dashboard.DashboardScreen
import com.joeabouserhal.financetracker.ui.goals.CompletedGoalsScreen
import com.joeabouserhal.financetracker.ui.goals.GoalsScreen
import com.joeabouserhal.financetracker.ui.main.MainTabs
import com.joeabouserhal.financetracker.ui.presets.PresetPickerScreen
import com.joeabouserhal.financetracker.ui.presets.PresetsScreen
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.settings.AccountDetailScreen
import com.joeabouserhal.financetracker.ui.settings.CurrenciesAccountsScreen
import com.joeabouserhal.financetracker.ui.settings.ThemesScreen
import com.joeabouserhal.financetracker.ui.transactions.TransactionFormScreen
import kotlinx.coroutines.launch

/**
 * Top-level Navigation 3 stack with auth bootstrap:
 *  - first launch / guest without a completed choice → AuthFlow
 *  - otherwise → Main tabs
 * Auth, the transaction form, and account screens are pushed on top.
 */
@Composable
fun MainNavigation(
  shortcutDestination: LauncherShortcutDestination? = null,
  onShortcutHandled: () -> Unit = {},
  onReady: () -> Unit = {},
  passwordRecoveryLink: String? = null,
  onRecoveryHandled: () -> Unit = {},
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = null)
  val authComplete by container.sessionManager.authChoiceCompleted.collectAsStateWithLifecycle(initialValue = null)

  val currentSession = session
  if (currentSession == null || authComplete == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("FINANCE TRACKER", style = MaterialTheme.typography.headlineMedium)
    }
    return
  }

  SideEffect { onReady() }
  if (passwordRecoveryLink != null) {
    com.joeabouserhal.financetracker.ui.auth.PasswordRecoveryScreen(passwordRecoveryLink, onRecoveryHandled)
    return
  }
  val needsAuth = currentSession.isGuest && authComplete != true
  val initial: NavKey = if (needsAuth) AuthFlow else Main

  key(initial, currentSession.ownerId) {
    val backStack = rememberNavBackStack(initial)
    LaunchedEffect(shortcutDestination, needsAuth) {
      if (shortcutDestination != null && !needsAuth) {
        when (shortcutDestination) {
          LauncherShortcutDestination.AddTransaction -> backStack.add(AddTransaction(presetId = null))
          LauncherShortcutDestination.AddFromPreset -> backStack.add(PresetPicker)
        }
        onShortcutHandled()
      }
    }
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      transitionSpec = {
        (slideInHorizontally(tween(260)) { it }) togetherWith
          (slideOutHorizontally(tween(260)) { -it / 3 })
      },
      popTransitionSpec = {
        (slideInHorizontally(tween(260)) { -it / 3 }) togetherWith
          (slideOutHorizontally(tween(260)) { it })
      },
      predictivePopTransitionSpec = { _ ->
        (slideInHorizontally(tween(260)) { -it / 3 }) togetherWith
          (slideOutHorizontally(tween(260)) { it })
      },
      entryProvider =
        entryProvider {
          entry<AuthFlow> {
            AuthScreen(
              onGuest = {
                scope.launch {
                  container.sessionManager.completeAuthChoice()
                  container.sessionManager.enterGuestSession()
                  // Pop the auth screen when it was pushed on top of Main.
                  if (backStack.size > 1 && backStack.lastOrNull() is AuthFlow) {
                    backStack.removeLastOrNull()
                  }
                }
              },
              onSignedIn = { userId ->
                scope.launch {
                  container.sessionManager.completeAuthChoice()
                  container.sessionManager.setUserSession(userId)
                  container.syncScheduler.startPeriodic()
                  container.syncScheduler.requestSyncNow()
                  // Pop the auth screen when it was pushed on top of Main —
                  // the session switch alone only recreates the stack when the
                  // INITIAL destination changes, not for a pushed AuthFlow.
                  if (backStack.size > 1 && backStack.lastOrNull() is AuthFlow) {
                    backStack.removeLastOrNull()
                  }
                }
              },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<Main> {
            MainTabs(
              onTransfer = { backStack.add(AddTransfer) },
              onAddTransaction = { backStack.add(AddTransaction(presetId = null)) },
              onOpenPresetPicker = { backStack.add(PresetPicker) },
              onEditTransaction = { transactionId -> backStack.add(EditTransaction(transactionId)) },
              onDuplicateTransaction = { backStack.add(AddTransaction(duplicateId = it)) },
              onOpenCurrenciesAccounts = { backStack.add(CurrenciesAccounts) },
              onOpenThemes = { backStack.add(Themes) },
              onOpenAppLock = { backStack.add(AppLock) },
              onOpenCategories = { backStack.add(Categories) },
              onOpenPresets = { backStack.add(Presets) },
              onOpenCompletedGoals = { backStack.add(CompletedGoals) },
              onOpenAuth = { backStack.add(AuthFlow) },
            )
          }
          entry<AddTransaction> { navKey ->
            TransactionFormScreen(
              ownerId = currentSession.ownerId,
              transactionId = null,
              presetId = navKey.presetId,
              duplicateId = navKey.duplicateId,
              onBack = {
                // ✕ from a preset-prefilled form skips the picker entirely
                // and returns straight to the page that opened the flow.
                if (navKey.presetId != null && backStack.getOrNull(backStack.size - 2) is PresetPicker) {
                  backStack.removeLastOrNull() // the form
                  backStack.removeLastOrNull() // the preset picker
                } else {
                  backStack.removeLastOrNull()
                }
              },
              onSaved = {
                // Saving from a preset-prefilled form also skips the picker:
                // the transaction is done, so land back on the origin page.
                if (navKey.presetId != null && backStack.getOrNull(backStack.size - 2) is PresetPicker) {
                  backStack.removeLastOrNull()
                  backStack.removeLastOrNull()
                } else {
                  backStack.removeLastOrNull()
                }
              },
            )
          }
          entry<AddTransfer> {
            TransactionFormScreen(
              ownerId = currentSession.ownerId,
              transactionId = null, transferMode = true,
              onBack = { backStack.removeLastOrNull() },
              onSaved = { backStack.removeLastOrNull() },
            )
          }
          entry<PresetPicker> {
            PresetPickerScreen(
              onBack = { backStack.removeLastOrNull() },
              onPick = { presetId ->
                // Keep the picker underneath so backing out of the form
                // returns to "Add from preset".
                backStack.add(AddTransaction(presetId = presetId))
              },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<EditTransaction> { navKey ->
            TransactionFormScreen(
              ownerId = currentSession.ownerId,
              transactionId = navKey.transactionId,
              onBack = { backStack.removeLastOrNull() },
              onSaved = { backStack.removeLastOrNull() },
            )
          }
          entry<CurrenciesAccounts> {
            CurrenciesAccountsScreen(
              ownerId = currentSession.ownerId,
              onBack = { backStack.removeLastOrNull() },
              onOpenAccount = { accountId -> backStack.add(AccountDetail(accountId)) },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<Themes> {
            ThemesScreen(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<AppLock> {
            com.joeabouserhal.financetracker.ui.security.AppLockScreen(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<Categories> {
            CategoriesScreen(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<Presets> {
            PresetsScreen(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<CompletedGoals> {
            CompletedGoalsScreen(
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
          entry<AccountDetail> { navKey ->
            AccountDetailScreen(
              accountId = navKey.accountId,
              onBack = { backStack.removeLastOrNull() },
              onEditTransaction = { transactionId -> backStack.add(EditTransaction(transactionId)) },
              modifier = Modifier.safeDrawingPadding(),
            )
          }
        },
    )
  }
}
