package com.joeabouserhal.financetracker.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.data.sync.SyncStatus
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Dates
import kotlinx.coroutines.launch

/** Settings hub for identity, app organization, synchronization, and account access. */
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

  var nameDraft by remember { mutableStateOf("") }
  var isEditingName by remember(session.ownerId) { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var isSavingName by remember { mutableStateOf(false) }
  var showSignOutConfirm by remember { mutableStateOf(false) }

  LaunchedEffect(session.ownerId, profile) {
    val loadedProfile = profile
    if (loadedProfile == null || loadedProfile.ownerId == session.ownerId) {
      nameDraft = loadedProfile?.name ?: ""
    }
  }

  val trimmedName = nameDraft.trim()
  val savedName = profile?.name?.trim().orEmpty()
  val nameChanged = trimmedName.isNotBlank() && trimmedName != savedName
  Column(modifier.fillMaxSize().background(spec.background)) {
    ScreenHeader(title = "Settings", subtitle = "PROFILE, DATA, AND SYNC")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 36.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      error?.let { message -> SettingsMessage(message = message, tone = spec.expense) }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(
          title = "PROFILE",
          description = "The name shown throughout your finance space.",
        )
        ProfilePanel(
          name = nameDraft,
          onNameChange = {
            nameDraft = it
            error = null
          },
          isGuest = session.isGuest,
          createdAt = profile?.createdAt,
          isEditingName = isEditingName,
          onStartEditing = {
            nameDraft = savedName
            isEditingName = true
            error = null
          },
          onCancelEditing = {
            nameDraft = savedName
            isEditingName = false
            error = null
          },
          canSave = nameChanged && !isSavingName,
          onSave = {
            scope.launch {
              isSavingName = true
              try {
                container.profileRepository.setName(session.ownerId, trimmedName)
                error = null
                isEditingName = false
              } catch (e: Exception) {
                error = e.message ?: "The profile name could not be saved."
              } finally {
                isSavingName = false
              }
            }
          },
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(
          title = "CUSTOMIZE & ORGANIZE",
          description = "Set up how the app looks and how your records are organized.",
        )
        SettingsDestinationGroup(
          destinations =
            listOf(
              SettingsDestination(
                title = "THEMES",
                description = "Change colors and appearance",
                iconRes = R.drawable.ic_tab_settings,
                onClick = onOpenThemes,
              ),
              SettingsDestination(
                title = "CURRENCIES & ACCOUNTS",
                description = "Manage balances, currencies, and defaults",
                iconRes = R.drawable.ic_tab_dashboard,
                onClick = onOpenCurrenciesAccounts,
              ),
              SettingsDestination(
                title = "CATEGORIES",
                description = "Organize income and expense activity",
                iconRes = R.drawable.ic_tab_categories,
                onClick = onOpenCategories,
              ),
              SettingsDestination(
                title = "PRESETS",
                description = "Reuse common transaction details",
                iconRes = R.drawable.ic_tab_presets,
                onClick = onOpenPresets,
              ),
            ),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(
          title = "SYNC",
          description = if (session.isGuest) "Guest records stay on this device." else "Keep this account consistent across devices.",
        )
        SyncPanel(
          status = syncStatus,
          onSync = { container.syncScheduler.requestSyncNow() },
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionHeader(
          title = "ACCOUNT",
          description = if (session.isGuest) "Connect an account when you want cloud sync." else "Manage access to this synced finance space.",
        )
        AccountPanel(
          isGuest = session.isGuest,
          onSignIn = onSignIn,
          onSignOut = { showSignOutConfirm = true },
        )
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
      Text(
        "Nothing is deleted. Your data stays on this device and in the cloud. Syncing stops until you sign back in.",
        style = MaterialTheme.typography.bodyMedium,
        color = LocalThemeSpec.current.ink,
      )
    }
  }
}

@Composable
private fun SettingsSectionHeader(
  title: String,
  description: String,
) {
  val spec = LocalThemeSpec.current
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = spec.accent,
      fontWeight = FontWeight.Bold,
    )
    Text(text = description, style = MaterialTheme.typography.bodySmall, color = spec.muted)
  }
}

@Composable
private fun ProfilePanel(
  name: String,
  onNameChange: (String) -> Unit,
  isGuest: Boolean,
  createdAt: String?,
  isEditingName: Boolean,
  onStartEditing: () -> Unit,
  onCancelEditing: () -> Unit,
  canSave: Boolean,
  onSave: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val displayName = name.trim().ifBlank { if (isGuest) "Guest profile" else "Your profile" }
  val initial = displayName.first().uppercaseChar().toString()
  val profileDetail =
    when {
      isGuest -> "LOCAL ONLY"
      !createdAt.isNullOrBlank() -> "MEMBER SINCE ${Dates.formatMonthYearLabel(createdAt).uppercase()}"
      else -> "SYNCED ACCOUNT"
    }

  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .border(spec.borderWidth, spec.border)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(48.dp).background(spec.accent), contentAlignment = Alignment.Center) {
        Text(
          text = initial,
          style = MaterialTheme.typography.titleLarge,
          color = spec.onAccent,
          fontWeight = FontWeight.Bold,
        )
      }
      Column(
        Modifier.weight(1f).padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = displayName,
          style = MaterialTheme.typography.titleMedium,
          color = spec.ink,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
        Text(profileDetail, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      }
      Text(
        text = if (isGuest) "GUEST" else "ACTIVE",
        style = MaterialTheme.typography.labelSmall,
        color = if (isGuest) spec.muted else spec.income,
        modifier =
          Modifier
            .border(spec.borderWidth, if (isGuest) spec.muted else spec.income)
            .padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }

    if (isEditingName) {
      BrTextField(
        value = name,
        onValueChange = onNameChange,
        label = "DISPLAY NAME",
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrButton(
          text = "CANCEL",
          onClick = onCancelEditing,
          style = BrButtonStyle.OUTLINE,
          compact = true,
          modifier = Modifier.weight(1f),
        )
        BrButton(
          text = "SAVE",
          onClick = onSave,
          enabled = canSave,
          compact = true,
          modifier = Modifier.weight(1f),
        )
      }
    } else {
      BrButton(
        text = "EDIT NAME",
        onClick = onStartEditing,
        style = BrButtonStyle.OUTLINE,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

private data class SettingsDestination(
  val title: String,
  val description: String,
  @param:DrawableRes val iconRes: Int,
  val onClick: () -> Unit,
)

@Composable
private fun SettingsDestinationGroup(destinations: List<SettingsDestination>) {
  val spec = LocalThemeSpec.current
  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .border(spec.borderWidth, spec.border),
  ) {
    destinations.forEachIndexed { index, destination ->
      SettingsDestinationRow(destination)
      if (index != destinations.lastIndex) {
        Box(
          Modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = 12.dp)
            .height(1.dp)
            .background(spec.border.copy(alpha = 0.42f)),
        )
      }
    }
  }
}

@Composable
private fun SettingsDestinationRow(destination: SettingsDestination) {
  val spec = LocalThemeSpec.current
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()

  Row(
    Modifier
      .fillMaxWidth()
      .background(if (pressed) spec.surfaceAlt else spec.surface)
      .minimumInteractiveComponentSize()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = destination.onClick,
      )
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(40.dp)
        .background(spec.surfaceAlt)
        .border(spec.borderWidth, spec.border.copy(alpha = 0.55f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(destination.iconRes),
        contentDescription = null,
        tint = spec.accent,
        modifier = Modifier.size(21.dp),
      )
    }
    Column(
      Modifier.weight(1f).padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(destination.title, style = MaterialTheme.typography.labelLarge, color = spec.ink)
      Text(destination.description, style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
    Icon(
      painter = painterResource(R.drawable.ic_chevron_right),
      contentDescription = null,
      tint = spec.muted,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun SyncPanel(
  status: SyncStatus,
  onSync: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val statusTone =
    when {
      status.isGuest -> spec.muted
      status.isSyncing -> spec.accent
      status.lastError != null -> spec.expense
      status.isOnline -> spec.income
      else -> spec.expense
    }
  val statusTitle =
    when {
      status.isGuest -> "LOCAL ONLY"
      status.isSyncing -> "SYNCING NOW"
      status.errorKind == "AUTH_REQUIRED" -> "SIGN IN REQUIRED"
      status.actionRequiredCount > 0 || status.errorKind == "ACTION_REQUIRED" -> "ACTION REQUIRED"
      status.lastError != null -> "RETRYING SYNC"
      status.pendingCount > 0 && !status.isOnline -> "WAITING FOR CONNECTION"
      status.pendingCount > 0 -> "CHANGES PENDING"
      status.isOnline && status.lastSyncAt != null -> "UP TO DATE"
      status.isOnline -> "READY TO SYNC"
      else -> "OFFLINE"
    }
  val statusDescription =
    when {
      status.isGuest -> "Sign in below to sync between devices."
      status.isSyncing -> "Sending and checking for your latest changes."
      status.pendingCount > 0 -> "Your local changes are safe and will retry automatically."
      status.isOnline -> "Local and cloud records are connected."
      else -> "New changes remain safe on this device until you reconnect."
    }

  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .border(spec.borderWidth, spec.border)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(Modifier.padding(top = 5.dp).size(10.dp).background(statusTone))
      Column(
        Modifier.weight(1f).padding(start = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(statusTitle, style = MaterialTheme.typography.labelLarge, color = statusTone)
        Text(statusDescription, style = MaterialTheme.typography.bodySmall, color = spec.muted)
      }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SyncMetric(
        label = "PENDING",
        value = status.pendingCount.toString(),
        modifier = Modifier.weight(0.34f),
      )
      SyncMetric(
        label = "LAST SYNC",
        value = status.lastSyncAt?.let(Dates::formatInstantLabel) ?: "NOT YET",
        modifier = Modifier.weight(0.66f),
      )
    }

    if (!status.isGuest && (status.retryingCount > 0 || status.actionRequiredCount > 0)) {
      Text(
        "${status.retryingCount} RETRYING  /  ${status.actionRequiredCount} NEED ATTENTION",
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
      )
    }
    if (!status.isGuest) status.lastError?.let { SettingsMessage(it, spec.expense) }

    if (!status.isGuest) {
      BrButton(
        text = if (status.isSyncing) "SYNCING..." else "SYNC NOW",
        onClick = onSync,
        enabled = !status.isSyncing,
        style = BrButtonStyle.OUTLINE,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun SyncMetric(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  Column(
    modifier
      .background(spec.surfaceAlt)
      .padding(horizontal = 11.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      color = spec.ink,
      fontWeight = FontWeight.Bold,
      maxLines = 2,
    )
  }
}

@Composable
private fun AccountPanel(
  isGuest: Boolean,
  onSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  Column(
    Modifier
      .fillMaxWidth()
      .background(spec.surface)
      .border(spec.borderWidth, spec.border)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        Modifier
          .padding(top = 4.dp)
          .size(width = 4.dp, height = 36.dp)
          .background(if (isGuest) spec.muted else spec.income),
      )
      Column(
        Modifier.weight(1f).padding(start = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = if (isGuest) "GUEST MODE" else "ACCOUNT CONNECTED",
          style = MaterialTheme.typography.labelLarge,
          color = spec.ink,
        )
        Text(
          text = if (isGuest) "No cloud account is connected." else "Cloud sync is enabled for this profile.",
          style = MaterialTheme.typography.bodySmall,
          color = spec.muted,
        )
      }
    }
    if (isGuest) {
      BrButton(
        text = "SIGN IN OR CREATE ACCOUNT",
        onClick = onSignIn,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
      )
    } else {
      BrButton(
        text = "SIGN OUT",
        onClick = onSignOut,
        style = BrButtonStyle.DANGER,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun SettingsMessage(
  message: String,
  tone: Color,
) {
  val spec = LocalThemeSpec.current
  Text(
    text = message,
    style = MaterialTheme.typography.bodySmall,
    color = tone,
    modifier =
      Modifier
        .fillMaxWidth()
        .background(tone.copy(alpha = 0.10f))
        .border(spec.borderWidth, tone.copy(alpha = 0.65f))
        .padding(horizontal = 11.dp, vertical = 9.dp),
  )
}
