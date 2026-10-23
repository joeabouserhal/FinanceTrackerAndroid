package com.joeabouserhal.financetracker.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.data.auth.AuthActionResult
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

@Composable
fun PasswordRecoveryScreen(link: String, onClose: () -> Unit) {
  val repo = rememberAppContainer().authRepository
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  // Passwords and recovery credentials deliberately never enter saveable state.
  var password by remember { mutableStateOf("") }
  var confirmation by remember { mutableStateOf("") }
  var ready by remember(link) { mutableStateOf(false) }
  var busy by remember { mutableStateOf(true) }
  var complete by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  fun close() { scope.launch { repo.clearRecovery(); onClose() } }
  BackHandler { if (!busy) close() }
  LaunchedEffect(link) {
    when (val result = repo.openPasswordRecovery(link)) {
      AuthActionResult.Success -> ready = true
      is AuthActionResult.Error -> error = "Could not open this reset link. It may have expired or been requested on another device. Request a new one on this phone."
    }
    busy = false
  }
  Column(Modifier.fillMaxSize().background(spec.background).safeDrawingPadding().imePadding()
    .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("RESET PASSWORD", style = MaterialTheme.typography.headlineMedium, color = spec.ink)
    if (complete) Text("Password updated. You can now sign in with your new password.", color = spec.ink)
    else if (ready) {
      Text("Choose a new password. Your local records and active profile will not change.", color = spec.muted)
      BrTextField(password, { password = it }, "NEW PASSWORD", modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
      BrTextField(confirmation, { confirmation = it }, "CONFIRM PASSWORD", modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
      BrButton(if (busy) "SAVING…" else "SAVE PASSWORD", enabled = !busy && password.isNotEmpty() && confirmation.isNotEmpty(), onClick = {
        busy = true; error = null
        scope.launch {
          when (val result = repo.updateRecoveredPassword(password, confirmation)) {
            AuthActionResult.Success -> { complete = true; password = ""; confirmation = "" }
            is AuthActionResult.Error -> error = result.message
          }
          busy = false
        }
      })
    }
    if (busy && !ready) Text("Checking reset link…", color = spec.muted)
    error?.let { Text(it, color = spec.expense) }
    BrButton(if (complete) "DONE" else "CANCEL", enabled = !busy, style = BrButtonStyle.OUTLINE, onClick = { close() })
  }
}
