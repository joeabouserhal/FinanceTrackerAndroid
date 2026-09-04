package com.joeabouserhal.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.transactions.TransactionDashedDivider

/** One scrolling surface: headings and controls never squeeze the list at large text sizes. */
@Composable
internal fun ManagementPage(
  title: String,
  description: String,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  action: String? = null,
  onAdd: () -> Unit = {},
  listTag: String = "management-list",
  compact: Boolean = false,
  content: LazyListScope.() -> Unit,
) {
  val spec = LocalThemeSpec.current
  Column(modifier.fillMaxSize().background(spec.background)) {
    if (onBack != null) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(painterResource(R.drawable.ic_chevron_right), "Back", Modifier.size(22.dp).graphicsLayer { rotationZ = 180f }, tint = spec.ink)
        }
        Text("YOUR FINANCES", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      }
    }
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth().testTag(listTag),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    ) {
      item(key = "page-header") {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp), modifier = Modifier.padding(bottom = if (compact) 10.dp else 20.dp)) {
          Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = spec.ink)
          Text(description, style = MaterialTheme.typography.bodySmall, color = spec.muted)
          if (action != null) {
            BrButton(action, onAdd, iconRes = R.drawable.ic_add, compact = compact,
              minHeight = if (compact) 48.dp else null, modifier = Modifier.padding(top = if (compact) 2.dp else 6.dp))
          }
        }
      }
      content()
    }
  }
}

@Composable
internal fun ManagementTypeFilter(selected: String, onSelect: (String) -> Unit) {
  val spec = LocalThemeSpec.current
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    BrChip("All", selected == "ALL", { onSelect("ALL") })
    BrChip("Expense", selected == "EXPENSE", { onSelect("EXPENSE") }, colorDot = spec.expense)
    BrChip("Income", selected == "INCOME", { onSelect("INCOME") }, colorDot = spec.income)
  }
}

@Composable
internal fun ManagementSection(title: String, count: Int? = null, compact: Boolean = false) {
  val spec = LocalThemeSpec.current
  Row(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 20.dp, bottom = if (compact) 6.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = spec.muted)
    if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = spec.muted)
  }
}

@Composable
internal fun ManagementListRow(
  title: String,
  description: String,
  onClick: () -> Unit,
  @DrawableRes icon: Int,
  modifier: Modifier = Modifier,
  tint: Color = LocalThemeSpec.current.muted,
  divider: Boolean = false,
  onManage: (() -> Unit)? = null,
  compact: Boolean = false,
  detail: (@Composable () -> Unit)? = null,
) {
  val spec = LocalThemeSpec.current
  Column(modifier.fillMaxWidth().background(spec.surface)) {
    if (divider) TransactionDashedDivider(Modifier.padding(horizontal = 12.dp).testTag("management-row-divider"))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
          .padding(start = 12.dp, end = 8.dp, top = if (compact) 9.dp else 15.dp, bottom = if (compact) 9.dp else 15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(34.dp).background(spec.surfaceAlt).border(1.dp, spec.border), contentAlignment = Alignment.Center) {
          Icon(painterResource(icon), null, Modifier.size(19.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
          Text(title, style = MaterialTheme.typography.titleMedium, color = spec.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
          if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = spec.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
          detail?.invoke()
        }
        if (onManage == null) Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp), tint = spec.muted)
      }
      if (onManage != null) IconButton(onClick = onManage, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_more), "Manage $title", Modifier.size(22.dp), tint = spec.muted)
      }
    }
  }
}

@Composable
internal fun ManagementEmptyState(title: String, description: String) {
  val spec = LocalThemeSpec.current
  Column(Modifier.fillMaxWidth().padding(top = 16.dp).background(spec.surface).border(1.dp, spec.border).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = spec.ink)
    Text(description, style = MaterialTheme.typography.bodySmall, color = spec.muted)
  }
}

internal data class ManagementAction(val label: String, val description: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
internal fun ManagementActions(title: String, onDismiss: () -> Unit, actions: List<ManagementAction>) {
  val spec = LocalThemeSpec.current
  BrDialog(title, onDismiss, dismissText = "CLOSE") {
    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
      actions.forEachIndexed { index, action ->
        if (index > 0) TransactionDashedDivider()
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onDismiss(); action.onClick() }.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(action.label, style = MaterialTheme.typography.labelLarge, color = if (action.destructive) spec.expense else spec.ink)
          Text(action.description, style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
      }
    }
  }
}
