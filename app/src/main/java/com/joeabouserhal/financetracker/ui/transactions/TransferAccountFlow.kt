package com.joeabouserhal.financetracker.ui.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrDialog

/** Equal-width account selectors with a compact, animated swap action between them. */
@Composable
internal fun TransferAccountFlow(
  accounts: List<AccountEntity>,
  sourceId: String?,
  destinationId: String?,
  currencyCode: String,
  onSourceChange: (String) -> Unit,
  onDestinationChange: (String) -> Unit,
  onSwap: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  var choosingSource by rememberSaveable { mutableStateOf<Boolean?>(null) }
  var swapRotation by remember { mutableFloatStateOf(0f) }
  val animatedRotation by animateFloatAsState(
    targetValue = swapRotation,
    animationSpec = tween(320, easing = FastOutSlowInEasing),
    label = "swapArrowRotation",
  )
  val swapAccounts = {
    swapRotation += 180f
    onSwap()
  }
  val source = accounts.firstOrNull { it.id == sourceId }
  val destination = accounts.firstOrNull { it.id == destinationId }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("ACCOUNTS", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(currencyCode, style = MaterialTheme.typography.labelSmall, color = spec.transfer)
    }
    Row(Modifier.fillMaxWidth().background(spec.surface).border(spec.borderWidth, spec.border),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TransferAccountEndpoint(
        label = "FROM", account = source,
        enabled = accounts.isNotEmpty(), onClick = { choosingSource = true },
        modifier = Modifier.weight(1f),
      )
      val canSwap = source != null && destination != null && sourceId != destinationId
      Column(
        Modifier.padding(vertical = 8.dp)
          .background(spec.transfer.copy(alpha = if (canSwap) 0.1f else 0.04f))
          .semantics { contentDescription = "Swap accounts" }
          .clickable(enabled = canSwap, role = Role.Button, onClick = swapAccounts)
          .widthIn(min = 48.dp).heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
      ) {
        Icon(painterResource(R.drawable.ic_transfer), contentDescription = null,
          tint = if (canSwap) spec.transfer else spec.muted.copy(alpha = 0.45f),
          modifier = Modifier.size(24.dp).rotate(animatedRotation))
        Text("SWAP", style = MaterialTheme.typography.labelSmall,
          color = if (canSwap) spec.transfer else spec.muted.copy(alpha = 0.45f))
      }
      TransferAccountEndpoint(
        label = "TO", account = destination,
        enabled = accounts.any { it.id != sourceId }, onClick = { choosingSource = false },
        modifier = Modifier.weight(1f),
      )
    }
    if (accounts.size < 2) {
      Text("Add a second $currencyCode account in Options → Currencies & accounts.",
        style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
  }

  choosingSource?.let { isSource ->
    val choices = if (isSource) accounts else accounts.filter { it.id != sourceId }
    val selectedId = if (isSource) sourceId else destinationId
    BrDialog(
      title = if (isSource) "Transfer from" else "Transfer to",
      onDismiss = { choosingSource = null }, dismissText = null,
    ) {
      Text("$currencyCode · ${if (isSource) "Source account" else "Destination account"}",
        style = MaterialTheme.typography.labelSmall, color = spec.muted)
      LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 320.dp).selectableGroup(),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(choices, key = { _, account -> account.id }) { index, account ->
          if (index > 0) TransactionDashedDivider()
          val selected = account.id == selectedId
          Row(
            Modifier.fillMaxWidth()
              .selectable(selected, role = Role.RadioButton, onClick = {
                if (isSource && account.id == destinationId) swapAccounts()
                else if (isSource) onSourceChange(account.id)
                else onDestinationChange(account.id)
                choosingSource = null
              })
              .heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(account.name, style = MaterialTheme.typography.bodyLarge, color = spec.ink)
              if (account.archived || isSource && account.id == destinationId) {
                Text(if (account.archived) "Archived" else "Current destination · swaps accounts",
                  style = MaterialTheme.typography.labelSmall, color = spec.muted)
              }
            }
            Box(Modifier.size(18.dp).border(1.dp, if (selected) spec.transfer else spec.border),
              contentAlignment = Alignment.Center) {
              if (selected) Box(Modifier.size(8.dp).background(spec.transfer))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TransferAccountEndpoint(
  label: String, account: AccountEntity?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
  val spec = LocalThemeSpec.current
  Column(
    modifier.heightIn(min = 88.dp).clickable(enabled = enabled, role = Role.Button,
      onClickLabel = if (label == "FROM") "Choose source account" else "Choose destination account", onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
        tint = if (enabled) spec.transfer else spec.muted.copy(alpha = 0.4f),
        modifier = Modifier.size(14.dp).rotate(90f))
    }
      AnimatedContent(
        targetState = account?.let { it.id to it.name },
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
          // Names slide toward/from the centre, following the horizontal route.
          val direction = if (label == "FROM") 1 else -1
          (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * direction } + fadeIn(tween(240)))
            .togetherWith(slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it * direction } + fadeOut(tween(120)))
        },
        label = "transferAccount$label",
      ) { displayAccount ->
        Text(displayAccount?.second ?: "Choose account", style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
          fontWeight = FontWeight.SemiBold, color = if (displayAccount == null) spec.muted else spec.ink,
          maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
  }
}
