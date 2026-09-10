package com.joeabouserhal.financetracker

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.launch.SplashExitAnimation
import com.joeabouserhal.financetracker.ui.launch.launchAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val MIN_SPLASH_MILLIS = 220L
private const val MAX_SPLASH_WAIT_MILLIS = 1_500L

private const val ACTION_ADD_TRANSACTION = "com.joeabouserhal.financetracker.action.ADD_TRANSACTION"
private const val ACTION_ADD_FROM_PRESET = "com.joeabouserhal.financetracker.action.ADD_FROM_PRESET"

enum class LauncherShortcutDestination {
  AddTransaction,
  AddFromPreset,
}

private fun android.content.Intent?.launcherShortcutDestination(): LauncherShortcutDestination? =
  when (this?.action) {
    ACTION_ADD_TRANSACTION -> LauncherShortcutDestination.AddTransaction
    ACTION_ADD_FROM_PRESET -> LauncherShortcutDestination.AddFromPreset
    else -> null
  }

class MainActivity : ComponentActivity() {
  private var splashExitAnimation: SplashExitAnimation? = null
  private val launcherShortcutDestination = MutableStateFlow<LauncherShortcutDestination?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    launcherShortcutDestination.value = intent.launcherShortcutDestination()

    val animateLaunch = savedInstanceState == null && launchAnimationsEnabled(this)
    var minimumElapsed = !animateLaunch
    var themeReady = false
    var destinationReady = false
    var waitExpired = false
    splashScreen.setKeepOnScreenCondition {
      !waitExpired && (!minimumElapsed || !themeReady || !destinationReady)
    }
    splashScreen.setOnExitAnimationListener { provider ->
      splashExitAnimation = SplashExitAnimation(provider.view, provider.iconView) {
        provider.remove()
        splashExitAnimation = null
      }
      splashExitAnimation?.start(
        animateLaunch && launchAnimationsEnabled(this) &&
          lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !isFinishing && !isDestroyed,
      )
    }
    lifecycleScope.launch {
      if (animateLaunch) delay(MIN_SPLASH_MILLIS)
      minimumElapsed = true
      findViewById<View>(android.R.id.content)?.invalidate()
      // Local bootstrap only; never hold launch indefinitely or wait for sync.
      delay(MAX_SPLASH_WAIT_MILLIS - if (animateLaunch) MIN_SPLASH_MILLIS else 0L)
      waitExpired = true
      findViewById<View>(android.R.id.content)?.invalidate()
    }

    enableEdgeToEdge()
    setContent {
      val shortcutDestination by launcherShortcutDestination.collectAsStateWithLifecycle()
      FinanceTrackerTheme(onReady = { themeReady = true }) {
        val darkTheme = LocalThemeSpec.current.isDark
        SideEffect {
          WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
          }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            shortcutDestination = shortcutDestination,
            onShortcutHandled = { launcherShortcutDestination.value = null },
            onReady = { destinationReady = true },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    launcherShortcutDestination.value = intent.launcherShortcutDestination()
  }

  override fun onStop() {
    splashExitAnimation?.cancel()
    super.onStop()
  }

  override fun onDestroy() {
    splashExitAnimation?.cancel()
    super.onDestroy()
  }
}
