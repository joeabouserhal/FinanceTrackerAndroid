package com.joeabouserhal.financetracker.ui.security

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.security.AppLockActivity
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle

/** Do not compose protected screens (including their dialog windows) until authenticated. */
@Composable
fun AppLockGate(activity: AppLockActivity, onLockedReady: () -> Unit = {}, content: @Composable () -> Unit) {
  val state by activity.appLockManager.state.collectAsState()
  val busy by activity.authenticating.collectAsState()
  val error by activity.authError.collectAsState()
  val savedScreens = rememberSaveableStateHolder()
  if (!state.locked) {
    savedScreens.SaveableStateProvider("protected-screens", content)
  } else {
    SideEffect(onLockedReady)
    val spec = LocalThemeSpec.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Icon(painterResource(R.drawable.ic_lock), null, tint = spec.accent, modifier = Modifier.size(44.dp))
      Spacer(Modifier.height(20.dp))
      Text(stringResource(R.string.app_lock_locked), color = spec.ink, style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(8.dp))
      Text(stringResource(R.string.app_lock_unlock_copy), color = spec.muted, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(24.dp))
      BrButton(stringResource(R.string.app_lock_unlock), { activity.requestAppAuthentication() }, enabled = !busy)
      error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = spec.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        BrButton(stringResource(R.string.app_lock_security_settings), {
          activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }, style = BrButtonStyle.OUTLINE)
      }
    }
    // Prompt once per lock. Cancel/back leaves a safe screen with an explicit retry action.
    LaunchedEffect(Unit) {
      if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) activity.requestAppAuthentication()
    }
    LifecycleResumeEffect(Unit) {
      // Runs on initial resume as well as returning from phone security settings.
      if (error == null) activity.requestAppAuthentication()
      onPauseOrDispose { }
    }
  }
}
