package com.joeabouserhal.financetracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPeriodDialog(period: ReportPeriod, today: LocalDate, onDismiss: () -> Unit, onSelect: (ReportPeriod) -> Unit) {
  val spec = LocalThemeSpec.current
  var kind by rememberSaveable { mutableStateOf(period.kind) }
  var year by rememberSaveable { mutableIntStateOf(period.month.year) }
  var month by rememberSaveable { mutableIntStateOf(period.month.monthValue) }
  val initialWindow = period.custom ?: period.window(today)
  val rangeState = rememberDateRangePickerState(
    initialSelectedStartDateMillis = initialWindow?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    initialSelectedEndDateMillis = initialWindow?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
  )
  val selectedMonth = YearMonth.of(year, month)
  val valid = when (kind) {
    ReportPeriodKind.MONTH -> selectedMonth <= YearMonth.from(today)
    ReportPeriodKind.CUSTOM -> rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null
    ReportPeriodKind.ALL -> true
  }
  BrDialog(title = "Report period", onDismiss = onDismiss, confirmText = "APPLY", confirmEnabled = valid, wide = true, onConfirm = {
    val custom = if (kind == ReportPeriodKind.CUSTOM) ReportWindow(
      Instant.ofEpochMilli(requireNotNull(rangeState.selectedStartDateMillis)).atZone(ZoneOffset.UTC).toLocalDate(),
      Instant.ofEpochMilli(requireNotNull(rangeState.selectedEndDateMillis)).atZone(ZoneOffset.UTC).toLocalDate(),
    ) else null
    onSelect(ReportPeriod(kind, selectedMonth, custom))
  }) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(ReportPeriodKind.MONTH to "MONTH", ReportPeriodKind.CUSTOM to "CUSTOM", ReportPeriodKind.ALL to "ALL TIME").forEach { (mode, label) ->
        BrChip(label, selected = kind == mode, onClick = { kind = mode }, comfortable = true)
      }
    }
    when (kind) {
      ReportPeriodKind.MONTH -> {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          YearArrow("Previous year", "‹", year > 1) { year-- }
          Text(year.toString(), style = MaterialTheme.typography.titleMedium, color = spec.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          YearArrow("Next year", "›", year < today.year) { year++ }
        }
        (1..12).chunked(4).forEach { row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { value ->
              val enabled = YearMonth.of(year, value) <= YearMonth.from(today)
              val selected = value == month
              Box(Modifier.weight(1f).heightIn(min = 48.dp).background(if (selected && enabled) spec.accent else spec.surfaceAlt)
                .clickable(enabled = enabled, role = Role.Button) { month = value }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(YearMonth.of(year, value).format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelMedium,
                  color = if (!enabled) spec.muted.copy(alpha = 0.4f) else if (selected) spec.onAccent else spec.ink)
              }
            }
          }
        }
      }
      ReportPeriodKind.CUSTOM -> DateRangePicker(state = rangeState, title = null, headline = null,
        modifier = Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.45f))
      ReportPeriodKind.ALL -> Text("All recorded activity, grouped by currency. Previous-period comparisons are hidden.", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
    }
  }
}

@Composable
private fun YearArrow(description: String, text: String, enabled: Boolean, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Box(Modifier.size(48.dp).semantics { contentDescription = description }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = if (enabled) spec.ink else spec.muted.copy(alpha = 0.4f))
  }
}
