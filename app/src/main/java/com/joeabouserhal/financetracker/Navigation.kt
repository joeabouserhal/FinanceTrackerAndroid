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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.joeabouserhal.financetracker.ui.auth.AuthScreen
import com.joeabouserhal.financetracker.ui.categories.CategoriesScreen
import com.joeabouserhal.financetracker.ui.main.MainTabs
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
fun MainNavigation() {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = null)
  val authComplete by container.sessionManager.authChoiceCompleted.collectAsStateWithLifecycle(initialValue = false)

  val currentSession = session
  if (currentSession == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("FINANCE TRACKER", style = MaterialTheme.typography.headlineMedium)
    }
    return
  }

  val needsAuth = currentSession.isGuest && !authComplete
  val initial: NavKey = if (needsAuth) AuthFlow else Main

  key(initial) {
    val backStack = rememberNavBackStack(initial)
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
              onAddTransaction = { backStack.add(AddTransaction) },
              onEditTransaction = { transactionId -> backStack.add(EditTransaction(transactionId)) },
              onOpenCurrenciesAccounts = { backStack.add(CurrenciesAccounts) },
              onOpenThemes = { backStack.add(Themes) },
              onOpenCategories = { backStack.add(Categories) },
              onOpenAuth = { backStack.add(AuthFlow) },
            )
          }
          entry<AddTransaction> {
            TransactionFormScreen(
              transactionId = null,
              onBack = { backStack.removeLastOrNull() },
              onSaved = { backStack.removeLastOrNull() },
            )
          }
          entry<EditTransaction> { navKey ->
            TransactionFormScreen(
              transactionId = navKey.transactionId,
              onBack = { backStack.removeLastOrNull() },
              onSaved = { backStack.removeLastOrNull() },
            )
          }
          entry<CurrenciesAccounts> {
            CurrenciesAccountsScreen(
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
          entry<Categories> {
            CategoriesScreen(
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
