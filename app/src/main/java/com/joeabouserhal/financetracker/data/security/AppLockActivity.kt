package com.joeabouserhal.financetracker.data.security

import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import com.joeabouserhal.financetracker.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared by every activity that exposes finance UI, including notification entry points. */
abstract class AppLockActivity : FragmentActivity() {
  val appLockManager get() = (application as FinanceTrackerApplication).container.appLockManager
  private lateinit var biometricPrompt: BiometricPrompt
  private var settingsCallback: ((Boolean) -> Unit)? = null
  private val promptState by lazy { ViewModelProvider(this)[AppLockPromptState::class.java] }
  private val mutableAuthenticating get() = promptState.authenticating
  val authenticating get() = mutableAuthenticating.asStateFlow()
  private val mutableAuthError get() = promptState.error
  val authError get() = mutableAuthError.asStateFlow()

  private val authenticators: Int get() =
    (if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG
    else BiometricManager.Authenticators.BIOMETRIC_WEAK) or BiometricManager.Authenticators.DEVICE_CREDENTIAL

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Register the new callback even on recreation; AndroidX retains an in-flight prompt.
    biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          mutableAuthenticating.value = false
          mutableAuthError.value = null
          appLockManager.authenticated()
          settingsCallback?.also { settingsCallback = null }?.invoke(true)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          mutableAuthenticating.value = false
          mutableAuthError.value = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> getString(R.string.app_lock_cancelled)
            else -> errString.toString()
          }
          settingsCallback?.also { settingsCallback = null }?.invoke(false)
        }
        // A non-matching fingerprint is not terminal; let the system prompt handle retries.
      })
    applyAppLockCapturePolicy(appLockManager.state.value.config)
    lifecycleScope.launch { appLockManager.state.collect { applyAppLockCapturePolicy(it.config) } }
  }

  fun requestAppAuthentication(onResult: ((Boolean) -> Unit)? = null) {
    if (mutableAuthenticating.value || isFinishing || isDestroyed ||
      !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    val available = BiometricManager.from(this).canAuthenticate(authenticators)
    if (available != BiometricManager.BIOMETRIC_SUCCESS) {
      mutableAuthError.value = when (available) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
          getString(R.string.app_lock_setup_needed)
        else -> getString(R.string.app_lock_unavailable)
      }
      onResult?.invoke(false)
      return
    }
    settingsCallback = onResult
    mutableAuthError.value = null
    mutableAuthenticating.value = true
    try {
      biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.app_lock_prompt_title))
        .setSubtitle(getString(R.string.app_lock_prompt_subtitle))
        .setAllowedAuthenticators(authenticators)
        .build())
    } catch (_: RuntimeException) {
      mutableAuthenticating.value = false
      mutableAuthError.value = getString(R.string.app_lock_unavailable)
      settingsCallback?.also { settingsCallback = null }?.invoke(false)
    }
  }
}

/** Retained over rotation, but never persisted or restored across process death. */
class AppLockPromptState : ViewModel() {
  val authenticating = MutableStateFlow(false)
  val error = MutableStateFlow<String?>(null)
}

/** Screenshot protection is opt-in: FLAG_SECURE also blocks screen mirroring such as scrcpy. */
internal fun Activity.applyAppLockCapturePolicy(config: AppLockConfig) {
  if (config.blockScreenCapture) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!config.enabled && !config.blockScreenCapture)
}
