package com.joeabouserhal.financetracker

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_SPLASH_MILLIS = 600L

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)

    // Hold the branded splash for a minimum duration so it doesn't just flash.
    var keepSplashOnScreen = true
    splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
    lifecycleScope.launch {
      delay(MIN_SPLASH_MILLIS)
      keepSplashOnScreen = false
      // The keep-on-screen check runs on the next pre-draw; invalidate so a
      // new draw pass is scheduled even if nothing in the UI changed.
      findViewById<View>(android.R.id.content)?.invalidate()
    }

    enableEdgeToEdge()
    setContent {
      FinanceTrackerTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
