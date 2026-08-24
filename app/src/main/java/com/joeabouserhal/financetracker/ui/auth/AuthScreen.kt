package com.joeabouserhal.financetracker.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.auth.AuthResult
import com.joeabouserhal.financetracker.data.auth.GoogleSignInResult
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

private enum class AuthMode(val label: String) { SIGN_IN("Sign in"), SIGN_UP("Create account") }

/**
 * Entry point for account choice: Email / Google / Continue as guest.
 * Guest mode never touches the network.
 */
@Composable
fun AuthScreen(
  onGuest: () -> Unit,
  onSignedIn: (userId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current

  var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun finish(result: AuthResult) {
    busy = false
    when (result) {
      is AuthResult.Success -> onSignedIn(result.userId)
      is AuthResult.Error -> error = result.message
    }
  }

  Column(
    modifier
      .fillMaxSize()
      .background(spec.background)
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Spacer(Modifier.height(24.dp))
    Text("FINANCE\nTRACKER", style = MaterialTheme.typography.displayMedium, color = spec.ink)
    Text("OFFLINE FIRST. SYNC WHEN YOU WANT.", style = MaterialTheme.typography.labelMedium, color = spec.muted)

    BrSegmentedToggle(
      options = listOf(AuthMode.SIGN_IN.label, AuthMode.SIGN_UP.label),
      selectedIndex = if (mode == AuthMode.SIGN_IN) 0 else 1,
      onSelect = { mode = if (it == 0) AuthMode.SIGN_IN else AuthMode.SIGN_UP },
    )

    BrTextField(
      value = email,
      onValueChange = { email = it },
      label = "EMAIL",
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    BrTextField(
      value = password,
      onValueChange = { password = it },
      label = "PASSWORD",
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      visualTransformation = PasswordVisualTransformation(),
    )

    error?.let {
      Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense)
    }

    BrButton(
      text = if (mode == AuthMode.SIGN_IN) "SIGN IN" else "CREATE ACCOUNT",
      onClick = {
        if (busy) return@BrButton
        if (email.isBlank() || password.isBlank()) {
          error = "Enter your email and password"
        } else {
          busy = true
          error = null
          scope.launch {
            val result = if (mode == AuthMode.SIGN_IN) {
              container.authRepository.signIn(email.trim(), password)
            } else {
              container.authRepository.signUp(email.trim(), password)
            }
            finish(result)
          }
        }
      },
      enabled = !busy,
      modifier = Modifier.fillMaxWidth(),
    )

    // Always show the Google option when Supabase itself is configured.
    // If the web client id is missing, tapping it explains the setup step.
    BrButton(
      text = if (busy) "PLEASE WAIT…" else "SIGN IN WITH GOOGLE",
      iconRes = R.drawable.ic_google,
      onClick = {
        if (busy) return@BrButton
        busy = true
        error = null
        scope.launch {
          when (val token = container.googleSignIn.getIdToken()) {
            is GoogleSignInResult.Success -> finish(container.authRepository.signInWithGoogle(token.idToken))
            is GoogleSignInResult.Failure -> {
              busy = false
              error = token.message
            }
          }
        }
      },
      enabled = !busy,
      style = BrButtonStyle.OUTLINE,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      androidx.compose.foundation.layout.Box(
        Modifier
          .weight(1f)
          .height(1.dp)
          .background(spec.border.copy(alpha = 0.45f)),
      )
      Text(" OR ", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      androidx.compose.foundation.layout.Box(
        Modifier
          .weight(1f)
          .height(1.dp)
          .background(spec.border.copy(alpha = 0.45f)),
      )
    }

    BrButton(
      text = "CONTINUE AS GUEST",
      onClick = onGuest,
      style = BrButtonStyle.INK,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      "Guest data stays on this device. You can sign in later from Settings.",
      style = MaterialTheme.typography.labelSmall,
      color = spec.muted,
    )
  }
}
