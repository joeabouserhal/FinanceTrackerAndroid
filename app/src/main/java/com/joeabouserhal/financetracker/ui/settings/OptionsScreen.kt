package com.joeabouserhal.financetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.data.sync.SyncStatus
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.OfflineIndicator
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import kotlinx.coroutines.launch

/**
 * Options hub: profile at the top (name + "been a user since"), then pages
 * (Themes / Currencies & Accounts / Categories), sync status, and account
 * actions with a deliberately "dangerous but not destructive" sign-out.
 */
@Composable
fun OptionsScreen(
  onOpenCurrenciesAccounts: () -> Unit,
  onOpenThemes: () -> Unit,
  onOpenCategories: () -> Unit,
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

  var nameDraft by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var showSignOutConfirm by remember { mutableStateOf(false) }

  // Sync the draft with the owner's profile whenever the partition OR the
  // loaded profile changes — but only when the profile belongs to the current
  // owner (the guest profile briefly emits before the real session arrives).
  // Typing is never clobbered because the draft only changes when the data
  // behind it changes.
  LaunchedEffect(session.ownerId, profile) {
    val p = profile
    if (p == null || p.ownerId == session.ownerId) {
      nameDraft = p?.name ?: ""
    }
  }

  Column(modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Options")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }

      // ------------------------------------------------------------------ PROFILE
      SectionTitle("PROFILE")
      BrTextField(nameDraft, { nameDraft = it }, "NAME", modifier = Modifier.fillMaxWidth())
      val savedName = profile?.name ?: ""
      val nameChanged = nameDraft.trim().isNotBlank() && nameDraft.trim() != savedName
      if (nameChanged) {
        BrButton(
          text = "SAVE NAME",
          onClick = {
            scope.launch {
              try {
                container.profileRepository.setName(session.ownerId, nameDraft)
                error = null
              } catch (e: Exception) { error = e.message }
            }
          },
          style = BrButtonStyle.OUTLINE,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (!session.isGuest) {
        profile?.createdAt?.takeIf { it.isNotBlank() }?.let { createdAt ->
          Text("Been a user since ${Dates.formatMonthYearLabel(createdAt)}", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        }
      }

      // ------------------------------------------------------------------ PAGES
      SectionTitle("PAGES")
      PageRow("THEMES", onOpenThemes)
      PageRow("CURRENCIES & ACCOUNTS", onOpenCurrenciesAccounts)
      PageRow("CATEGORIES", onOpenCategories)

      // ------------------------------------------------------------------ SYNC
      SectionTitle("SYNC")
      Row(verticalAlignment = Alignment.CenterVertically) {
        OfflineIndicator(isOffline = syncStatus.isGuest || !syncStatus.isOnline)
        Spacer(Modifier.size(8.dp))
        Text(
          syncStatusLine(syncStatus),
          style = MaterialTheme.typography.labelSmall,
          color = spec.muted,
        )
      }
      syncStatus.lastSyncAt?.let { lastSync ->
        Text("LAST SYNC: ${Dates.formatInstantLabel(lastSync)}", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      }
      if (!syncStatus.isGuest) {
        BrButton(
          text = if (syncStatus.isSyncing) "SYNCING…" else "SYNC NOW",
          onClick = { container.syncScheduler.requestSyncNow() },
          enabled = !syncStatus.isSyncing,
          style = BrButtonStyle.OUTLINE,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      // ------------------------------------------------------------------ ACCOUNT
      SectionTitle("ACCOUNT")
      if (session.isGuest) {
        Text("Signed in as: Guest", style = MaterialTheme.typography.bodyMedium, color = spec.ink)
        BrButton(text = "SIGN IN / CREATE ACCOUNT", onClick = onSignIn, modifier = Modifier.fillMaxWidth())
      } else {
        BrButton(
          text = "SIGN OUT",
          onClick = { showSignOutConfirm = true },
          style = BrButtonStyle.DANGER,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.padding(vertical = 24.dp))
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
          // Show the login screen again after signing out.
          container.sessionManager.resetAuthChoice()
          showSignOutConfirm = false
        }
      },
    ) {
      Text(
        "Nothing is deleted — your data stays on this device and in the cloud. Syncing stops until you sign back in.",
        style = MaterialTheme.typography.bodyMedium,
        color = LocalThemeSpec.current.ink,
      )
    }
  }
}

@Composable
private fun PageRow(label: String, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Row(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .minimumInteractiveComponentSize()
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f))
    Text(">", style = MaterialTheme.typography.labelLarge, color = spec.accent)
  }
}

@Composable
private fun SectionTitle(text: String) {
  val spec = LocalThemeSpec.current
  Text(text, style = MaterialTheme.typography.labelMedium, color = spec.muted, modifier = Modifier.padding(top = 8.dp))
}

private fun syncStatusLine(status: SyncStatus): String =
  when {
    status.isGuest -> "Guest data stays on this device."
    status.isSyncing -> "Syncing now…"
    status.pendingCount > 0 -> "${status.pendingCount} change${if (status.pendingCount == 1) "" else "s"} waiting to sync"
    status.isOnline -> "Up to date"
    else -> "Offline — changes sync when you're back online"
  }
