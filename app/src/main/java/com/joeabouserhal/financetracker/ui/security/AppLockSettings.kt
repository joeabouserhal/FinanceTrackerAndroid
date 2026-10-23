package com.joeabouserhal.financetracker.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.security.AppLockActivity
import com.joeabouserhal.financetracker.data.security.AppLockConfig
import com.joeabouserhal.financetracker.data.security.AppLockTiming
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private tailrec fun Context.lockActivity(): AppLockActivity? = when (this) {
  is AppLockActivity -> this
  is ContextWrapper -> baseContext.lockActivity()
  else -> null
}

@Composable
fun AppLockSettings() {
  val activity = LocalContext.current.lockActivity() ?: return
  val state by activity.appLockManager.state.collectAsState()
  val authenticating by activity.authenticating.collectAsState()
  val authError by activity.authError.collectAsState()
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var minutes by rememberSaveable(state.config.minutes) { mutableStateOf(state.config.minutes.toString()) }
  fun save(config: AppLockConfig, authenticated: Boolean) {
    saving = true
    activity.lifecycleScope.launch {
      try {
        if (authenticated) activity.appLockManager.saveAfterAuthentication(config)
        else activity.appLockManager.setOptions(config.timing, config.minutes, config.blockScreenCapture)
        error = null
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { error = e.message }
      finally { saving = false }
    }
  }
  AppLockSettingsContent(state.config, minutes, { minutes = it; error = null },
    enabled = !saving && !authenticating,
    error = error ?: authError,
    onToggle = { enabled ->
      error = null
      activity.requestAppAuthentication { success ->
        if (success) save(state.config.copy(enabled = enabled), authenticated = true)
      }
    },
    onTiming = { timing -> save(state.config.copy(timing = timing), authenticated = false) },
    onApplyMinutes = { minutes.toIntOrNull()?.takeIf { it in 1..1440 }?.let {
      save(state.config.copy(minutes = it), authenticated = false)
    } },
    onCaptureProtection = { save(state.config.copy(blockScreenCapture = it), authenticated = false) },
  )
}

/** Stateless rendering lets device tests exercise settings without altering the real lock. */
@Composable
internal fun AppLockSettingsContent(
  config: AppLockConfig,
  minutes: String,
  onMinutes: (String) -> Unit,
  enabled: Boolean,
  error: String?,
  onToggle: (Boolean) -> Unit,
  onTiming: (AppLockTiming) -> Unit,
  onApplyMinutes: () -> Unit,
  onCaptureProtection: (Boolean) -> Unit = {},
) {
  val spec = LocalThemeSpec.current
  val density = LocalDensity.current
  val labelOverhang = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() / 2 }
  var minutesInputHeight by remember { mutableStateOf(56.dp) }
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.app_lock_title), color = spec.ink, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.app_lock_description), color = spec.muted, style = MaterialTheme.typography.bodySmall)
      }
      val lockLabel = stringResource(R.string.app_lock_title)
      Switch(config.enabled, onCheckedChange = onToggle, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = lockLabel },
        colors = SwitchDefaults.colors(checkedThumbColor = spec.onAccent, checkedTrackColor = spec.accent,
          uncheckedThumbColor = spec.muted, uncheckedTrackColor = spec.surface))
    }
    if (config.enabled) {
      Text(stringResource(R.string.app_lock_when), color = spec.muted, style = MaterialTheme.typography.labelSmall)
      Column(Modifier.fillMaxWidth().selectableGroup()) {
        AppLockTiming.entries.forEach { timing ->
          val selected = config.timing == timing
          Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (selected) Modifier.background(spec.surface) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onTiming(timing) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(20.dp)
              .background(if (selected) spec.accent else androidx.compose.ui.graphics.Color.Transparent)
              .border(1.dp, if (selected) spec.accent else spec.border), contentAlignment = Alignment.Center) {
              if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = spec.onAccent, modifier = Modifier.size(14.dp))
            }
            Text(stringResource(when (timing) {
              AppLockTiming.APP_RESTART -> R.string.app_lock_restart
              AppLockTiming.LEAVE_APP -> R.string.app_lock_leave
              AppLockTiming.AFTER_MINUTES -> R.string.app_lock_timeout
            }).uppercase(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
              color = if (!enabled) spec.muted else if (selected) spec.accent else spec.ink)
          }
          if (timing != AppLockTiming.entries.last()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.3f)))
          }
        }
      }
      Text(stringResource(when (config.timing) {
        AppLockTiming.APP_RESTART -> R.string.app_lock_restart_help
        AppLockTiming.LEAVE_APP -> R.string.app_lock_leave_help
        AppLockTiming.AFTER_MINUTES -> R.string.app_lock_timeout_help
      }), color = spec.muted, style = MaterialTheme.typography.bodySmall)
      if (config.timing == AppLockTiming.AFTER_MINUTES) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          BrTextField(minutes, { if (it.length <= 4 && it.all(Char::isDigit)) onMinutes(it) },
            stringResource(R.string.app_lock_minutes), modifier = Modifier.weight(1f)
              .testTag("app-lock-timeout-input")
              .onSizeChanged { size ->
                // Match the outlined container, excluding its floating label above the top border.
                minutesInputHeight = (with(density) { size.height.toDp() } - labelOverhang).coerceAtLeast(48.dp)
              }, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
          BrButton(stringResource(R.string.app_lock_apply), onApplyMinutes,
            modifier = Modifier.widthIn(max = 112.dp), compact = true, fillWidth = false,
            minHeight = minutesInputHeight, style = BrButtonStyle.OUTLINE,
            enabled = enabled && minutes.toIntOrNull()?.let { it in 1..1440 && it != config.minutes } == true)
        }
        if (minutes.toIntOrNull()?.let { it in 1..1440 } != true) {
          Text(stringResource(R.string.app_lock_minutes_error), color = spec.expense, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.app_lock_capture), color = spec.ink, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.app_lock_capture_help), color = spec.muted, style = MaterialTheme.typography.bodySmall)
      }
      val captureLabel = stringResource(R.string.app_lock_capture)
      Switch(config.blockScreenCapture, onCheckedChange = onCaptureProtection, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = captureLabel },
        colors = SwitchDefaults.colors(checkedThumbColor = spec.onAccent, checkedTrackColor = spec.accent,
          uncheckedThumbColor = spec.muted, uncheckedTrackColor = spec.surface))
    }
    if (config.enabled) Text(stringResource(R.string.app_lock_note), color = spec.muted, style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = spec.expense, style = MaterialTheme.typography.bodySmall) }
  }
}
