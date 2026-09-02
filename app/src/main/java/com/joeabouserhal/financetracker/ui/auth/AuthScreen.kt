package com.joeabouserhal.financetracker.ui.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
  var showPassword by rememberSaveable { mutableStateOf(false) }

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
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 18.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Spacer(Modifier.height(6.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(Modifier.size(11.dp).background(spec.accent))
      Text("PRIVATE LEDGER / V1", style = MaterialTheme.typography.labelSmall, color = spec.muted)
    }
    Text("FINANCE\nTRACKER", style = MaterialTheme.typography.displayMedium, color = spec.ink)
    Text(
      "YOUR MONEY, STORED LOCALLY.\nSYNCED ONLY WHEN YOU'RE ONLINE.",
      style = MaterialTheme.typography.labelMedium,
      color = spec.muted,
    )

    Box(Modifier.width(34.dp).height(3.dp).background(spec.accent))

    Column(
      Modifier.fillMaxWidth().background(spec.surface),
    ) {
      Box(Modifier.fillMaxWidth().height(2.dp).background(spec.accent))
      Column(
        Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text("ACCOUNT ACCESS", style = MaterialTheme.typography.labelMedium, color = spec.accent)

        BrSegmentedToggle(
          options = listOf(AuthMode.SIGN_IN.label, AuthMode.SIGN_UP.label),
          selectedIndex = if (mode == AuthMode.SIGN_IN) 0 else 1,
          onSelect = {
            mode = if (it == 0) AuthMode.SIGN_IN else AuthMode.SIGN_UP
            error = null
          },
        )

        BrTextField(
          value = email,
          onValueChange = { email = it },
          label = "EMAIL",
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          leadingIconRes = R.drawable.ic_email,
        )
        BrTextField(
          value = password,
          onValueChange = { password = it },
          label = "PASSWORD",
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          leadingIconRes = R.drawable.ic_lock,
          trailingIconRes = if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
          trailingIconDescription = if (showPassword) "Hide password" else "Show password",
          onTrailingIconClick = { showPassword = !showPassword },
        )

        error?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            color = spec.expense,
            modifier =
              Modifier
                .fillMaxWidth()
                .background(spec.surfaceAlt)
                .border(spec.borderWidth, spec.expense)
                .padding(12.dp),
          )
        }

        BrButton(
          text = if (busy) "PLEASE WAIT…" else if (mode == AuthMode.SIGN_IN) "SIGN IN" else "CREATE ACCOUNT",
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

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f).height(1.dp).background(spec.border.copy(alpha = 0.45f)))
          Text("  OR  ", style = MaterialTheme.typography.labelSmall, color = spec.muted)
          Box(Modifier.weight(1f).height(1.dp).background(spec.border.copy(alpha = 0.45f)))
        }

        // This asset keeps its official colors and has dedicated breathing room;
        // it must not inherit the monochrome icon tint used elsewhere.
        BrButton(
          text = if (busy) "PLEASE WAIT…" else "CONTINUE WITH GOOGLE",
          iconRes = R.drawable.ic_google,
          iconSize = 22.dp,
          preserveIconColors = true,
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
      }
    }

    Text("NO ACCOUNT?", style = MaterialTheme.typography.labelMedium, color = spec.muted)
    BrButton(
      text = "CONTINUE AS GUEST",
      onClick = onGuest,
      style = BrButtonStyle.INK,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      "Guest mode stays entirely on this device. Sign in later from Options without uploading guest records.",
      style = MaterialTheme.typography.labelSmall,
      color = spec.muted,
    )
    Spacer(Modifier.height(8.dp))
  }
}
