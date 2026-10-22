package com.joeabouserhal.financetracker.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.data.sync.SyncStatus
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import kotlinx.coroutines.launch

/** A compact settings directory with a single profile and sync summary. */
@Composable
fun OptionsScreen(
  onOpenCurrenciesAccounts: () -> Unit,
  onOpenThemes: () -> Unit,
  onOpenCategories: () -> Unit,
  onOpenPresets: () -> Unit,
  onSignIn: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val syncStatus by container.syncStatusRepository.observe().collectAsStateWithLifecycle(initialValue = SyncStatus())
  val profile by remember(session.ownerId) { container.profileRepository.observeProfile(session.ownerId) }
    .collectAsStateWithLifecycle(initialValue = null)

  var nameDraft by remember(session.ownerId) { mutableStateOf("") }
  var isEditingName by remember(session.ownerId) { mutableStateOf(false) }
  var error by remember(session.ownerId) { mutableStateOf<String?>(null) }
  var isSavingName by remember(session.ownerId) { mutableStateOf(false) }
  var showSignOutConfirm by remember(session.ownerId) { mutableStateOf(false) }
  val currentProfile = profile?.takeIf { it.ownerId == session.ownerId }
  val savedName = currentProfile?.name?.trim().orEmpty()
  LaunchedEffect(session.ownerId, currentProfile, isEditingName) {
    if (!isEditingName) nameDraft = savedName
  }

  val scrollState = rememberLazyListState()
  Column(modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Options")
    LazyColumn(
      state = scrollState,
      modifier = Modifier.fillMaxSize().imePadding(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item("profile") {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
          Text(
            savedName.ifBlank { if (session.isGuest) "Guest" else "Your profile" },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = spec.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
          )
          val profileDetail = when {
            session.isGuest -> "Guest profile · On this device"
            !currentProfile?.createdAt.isNullOrBlank() -> "Cloud account · Since ${Dates.formatMonthYearLabel(requireNotNull(currentProfile).createdAt)}"
            else -> "Cloud account"
          }
          Text(profileDetail, style = MaterialTheme.typography.bodySmall, color = spec.muted,
            modifier = Modifier.padding(top = 3.dp))
          if (isEditingName) {
            Spacer(Modifier.height(12.dp))
            BrTextField(nameDraft, onValueChange = { nameDraft = it; error = null }, label = "DISPLAY NAME")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextAction("CANCEL", enabled = !isSavingName, tone = spec.muted, onClick = {
                nameDraft = savedName; isEditingName = false; error = null
              })
              TextAction("SAVE NAME", enabled = nameDraft.trim().isNotBlank() && nameDraft.trim() != savedName && !isSavingName,
                onClick = {
                  scope.launch {
                    isSavingName = true
                    try {
                      container.profileRepository.setName(session.ownerId, nameDraft.trim())
                      error = null
                      isEditingName = false
                    } catch (e: Exception) {
                      error = e.message ?: "The name could not be saved."
                    } finally { isSavingName = false }
                  }
                })
            }
          } else {
            TextAction("EDIT NAME", onClick = { nameDraft = savedName; isEditingName = true; error = null },
              horizontalPadding = 0)
          }
          error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
        }
      }

      item("appearance") {
        SettingsGroup("APPEARANCE") {
          DestinationRow("Themes", R.drawable.ic_tab_settings, onOpenThemes)
        }
      }

      item("ledger") {
        SettingsGroup("YOUR LEDGER") {
          DestinationRow("Currencies & accounts", R.drawable.ic_tab_dashboard, onOpenCurrenciesAccounts)
          SettingsDivider()
          DestinationRow("Categories", R.drawable.ic_tab_categories, onOpenCategories)
          SettingsDivider()
          DestinationRow("Presets", R.drawable.ic_tab_presets, onOpenPresets)
        }
      }

      item("sync") {
        SettingsGroup("SYNC") {
          SyncSummary(syncStatus, onSync = { container.syncScheduler.requestSyncNow() })
        }
      }

      item("account-access") {
        if (session.isGuest) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextAction("SIGN IN OR CREATE ACCOUNT", onClick = onSignIn, horizontalPadding = 0)
            Text("Signing in opens your cloud profile. Guest records stay separate on this device.",
              style = MaterialTheme.typography.bodySmall, color = spec.muted)
          }
        } else {
          TextAction("SIGN OUT", onClick = { showSignOutConfirm = true }, tone = spec.expense, horizontalPadding = 0)
        }
      }
    }
  }

  if (showSignOutConfirm) {
    BrDialog(
      title = "SIGN OUT?",
      onDismiss = { showSignOutConfirm = false },
      confirmText = "SIGN OUT",
      onConfirm = {
        scope.launch {
          container.authRepository.signOut()
          container.sessionManager.signOutToGuest()
          container.syncScheduler.cancelAll()
          container.sessionManager.resetAuthChoice()
          showSignOutConfirm = false
        }
      },
    ) {
      Text("Nothing is deleted. Your data stays on this device and in the cloud. Syncing stops until you sign back in.",
        style = MaterialTheme.typography.bodyMedium, color = spec.ink)
    }
  }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.labelSmall, color = spec.muted,
      modifier = Modifier.semantics { heading() })
    Column(Modifier.fillMaxWidth().background(spec.surface), content = content)
  }
}

@Composable
private fun SettingsDivider() {
  val spec = LocalThemeSpec.current
  Box(Modifier.fillMaxWidth().padding(start = 48.dp, end = 14.dp).height(1.dp).background(spec.border.copy(alpha = 0.35f)))
}

@Composable
private fun DestinationRow(title: String, @DrawableRes iconRes: Int, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Row(
    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(painterResource(iconRes), contentDescription = null, tint = spec.muted, modifier = Modifier.size(22.dp))
    Text(title, style = MaterialTheme.typography.bodyLarge, color = spec.ink, modifier = Modifier.weight(1f))
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
      tint = spec.muted, modifier = Modifier.size(18.dp))
  }
}

@Composable
private fun SyncSummary(status: SyncStatus, onSync: () -> Unit) {
  val spec = LocalThemeSpec.current
  val tone = when {
    status.isGuest -> spec.muted
    status.isSyncing -> spec.accent
    status.lastError != null || status.actionRequiredCount > 0 -> spec.expense
    status.isOnline -> spec.income
    else -> spec.muted
  }
  val title = when {
    status.isGuest -> "On this device"
    status.isSyncing -> "Syncing"
    status.errorKind == "AUTH_REQUIRED" -> "Sign in required"
    status.actionRequiredCount > 0 || status.errorKind == "ACTION_REQUIRED" -> "Needs attention"
    status.lastError != null -> "Retrying sync"
    status.pendingCount > 0 && !status.isOnline -> "Waiting for connection"
    status.pendingCount > 0 -> "Changes pending"
    status.isOnline && status.lastSyncAt != null -> "Up to date"
    status.isOnline -> "Ready to sync"
    else -> "Offline"
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(6.dp).background(tone))
      Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
        color = spec.ink, modifier = Modifier.weight(1f))
      if (!status.isGuest) {
        TextAction(if (status.isSyncing) "SYNCING…" else "SYNC NOW", enabled = !status.isSyncing,
          onClick = onSync, horizontalPadding = 0)
      }
    }
    if (status.isGuest) {
      Text("Guest records are stored locally.", style = MaterialTheme.typography.bodySmall, color = spec.muted,
        modifier = Modifier.padding(bottom = 6.dp))
    } else {
      val pending = if (status.pendingCount == 0) "No pending changes" else "${status.pendingCount} pending"
      val lastSync = status.lastSyncAt?.let { "Last synced ${Dates.formatInstantLabel(it)}" } ?: "No sync yet"
      Text("$pending · $lastSync", style = MaterialTheme.typography.bodySmall, color = spec.muted,
        modifier = Modifier.padding(bottom = 6.dp))
      if (status.retryingCount > 0 || status.actionRequiredCount > 0) {
        Text("${status.retryingCount} retrying · ${status.actionRequiredCount} need attention",
          style = MaterialTheme.typography.bodySmall, color = spec.muted)
      }
      status.lastError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense,
          modifier = Modifier.padding(bottom = 6.dp))
      }
    }
  }
}

/** Flat secondary action; keeps a reliable touch target without another frame. */
@Composable
private fun TextAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tone: Color = LocalThemeSpec.current.accent,
  horizontalPadding: Int = 12,
) {
  val spec = LocalThemeSpec.current
  Box(
    modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .heightIn(min = 48.dp).padding(horizontal = horizontalPadding.dp, vertical = 10.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium,
      color = if (enabled) tone else spec.muted.copy(alpha = 0.5f))
  }
}
