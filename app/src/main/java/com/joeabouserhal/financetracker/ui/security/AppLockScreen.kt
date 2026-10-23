package com.joeabouserhal.financetracker.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.ScreenHeader

@Composable
fun AppLockScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  AppLockPage(onBack, modifier) { AppLockSettings() }
}

@Composable
internal fun AppLockPage(onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val spec = LocalThemeSpec.current
  Column(modifier.fillMaxSize().background(spec.background)) {
    Text(stringResource(R.string.app_lock_back), color = spec.accent, style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(start = 16.dp, top = 8.dp).heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp, vertical = 14.dp))
    ScreenHeader(stringResource(R.string.app_lock_title))
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
      .padding(start = 6.dp, end = 6.dp, bottom = 24.dp)) {
      content()
    }
  }
}
