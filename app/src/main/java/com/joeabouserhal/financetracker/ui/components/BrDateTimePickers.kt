package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Every row contains exactly seven cells, including leading/trailing empty days. */
internal fun calendarWeeks(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> {
  val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
  val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
  return (cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }).chunked(7)
}

@Composable
fun BrDatePickerDialog(
  title: String,
  initialDate: LocalDate,
  minimumDate: LocalDate? = null,
  onDismiss: () -> Unit,
  onSelect: (LocalDate) -> Unit,
) {
  var selected by remember(initialDate) { mutableStateOf(initialDate) }
  BrDialog(title, onDismiss, confirmText = "APPLY", wide = true, scrollContent = true,
    onConfirm = { onSelect(selected) }) {
    Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
      style = MaterialTheme.typography.bodyMedium, color = LocalThemeSpec.current.muted)
    BrCalendar(initialDate, selected, minimumDate = minimumDate, onSelect = { selected = it })
  }
}

/** Compact range selection without the fixed minimum width of Material's calendar. */
@Composable
fun BrDateRangePicker(
  start: LocalDate?,
  end: LocalDate?,
  onChange: (LocalDate?, LocalDate?) -> Unit,
) {
  val spec = LocalThemeSpec.current
  val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("FROM", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(start?.format(format) ?: "Start date", style = MaterialTheme.typography.bodyMedium, color = spec.ink)
    }
    Text("→", color = spec.muted)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("TO", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(end?.format(format) ?: "End date", style = MaterialTheme.typography.bodyMedium, color = spec.ink)
    }
  }
  Text(if (start != null && end == null) "Choose the end date" else "Choose a start date to begin a new range",
    style = MaterialTheme.typography.bodySmall, color = spec.muted)
  BrCalendar(start ?: LocalDate.now(), start, rangeEnd = end, onSelect = { day ->
    when {
      start == null || end != null -> onChange(day, null)
      day < start -> onChange(day, start)
      else -> onChange(start, day)
    }
  })
}

@Composable
private fun BrCalendar(
  initialDate: LocalDate,
  selected: LocalDate?,
  minimumDate: LocalDate? = null,
  rangeEnd: LocalDate? = null,
  onSelect: (LocalDate) -> Unit,
) {
  val spec = LocalThemeSpec.current
  val locale = Locale.getDefault()
  val today = LocalDate.now()
  var month by remember { mutableStateOf(YearMonth.from(initialDate)) }
  var choosingMonth by remember { mutableStateOf(false) }
  val firstDay = WeekFields.of(locale).firstDayOfWeek
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      CalendarArrow("Previous ${if (choosingMonth) "year" else "month"}", "‹") {
        month = if (choosingMonth) month.minusYears(1) else month.minusMonths(1)
      }
      Box(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { choosingMonth = !choosingMonth },
        contentAlignment = Alignment.Center) {
        Text(if (choosingMonth) month.year.toString() else month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
          style = MaterialTheme.typography.titleMedium, color = spec.ink, textAlign = TextAlign.Center)
      }
      CalendarArrow("Next ${if (choosingMonth) "year" else "month"}", "›") {
        month = if (choosingMonth) month.plusYears(1) else month.plusMonths(1)
      }
    }
    if (choosingMonth) {
      (1..12).chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
          row.forEach { value ->
            Box(Modifier.weight(1f).heightIn(min = 56.dp)
              .background(if (value == month.monthValue) spec.accent else Color.Transparent)
              .clickable(role = Role.Button) { month = month.withMonth(value); choosingMonth = false },
              contentAlignment = Alignment.Center) {
              Text(YearMonth.of(month.year, value).format(DateTimeFormatter.ofPattern("MMM", locale)),
                style = MaterialTheme.typography.labelLarge, color = if (value == month.monthValue) spec.onAccent else spec.ink)
            }
          }
        }
      }
    } else {
      Row(Modifier.fillMaxWidth()) {
        repeat(7) { index ->
          val day = firstDay.plus(index.toLong())
          Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
            Text(day.getDisplayName(java.time.format.TextStyle.NARROW_STANDALONE, locale),
              style = MaterialTheme.typography.labelSmall, color = spec.muted)
          }
        }
      }
      calendarWeeks(month, firstDay).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
          week.forEach { day ->
            val enabled = day != null && (minimumDate == null || day >= minimumDate)
            val endpoint = day != null && (day == selected || day == rangeEnd)
            val inRange = day != null && selected != null && rangeEnd != null && day > selected && day < rangeEnd
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
              .background(when { endpoint -> spec.accent; inRange -> spec.accent.copy(alpha = 0.12f); else -> Color.Transparent })
              .then(if (day == null) Modifier else Modifier
                .semantics { contentDescription = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }
                .selectable(endpoint, enabled = enabled, role = Role.Button) { onSelect(day) }),
              contentAlignment = Alignment.Center) {
              if (day != null) {
                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge,
                  color = when { endpoint -> spec.onAccent; !enabled -> spec.muted.copy(alpha = 0.4f); day == today -> spec.accent; else -> spec.ink })
              }
            }
          }
        }
      }
    }
    Box(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
      .clickable(role = Role.Button) {
        val date = today.coerceAtLeast(minimumDate ?: LocalDate.MIN)
        month = YearMonth.from(date); choosingMonth = false; onSelect(date)
      }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
      Text("TODAY", style = MaterialTheme.typography.labelMedium, color = spec.muted)
    }
  }
}

@Composable
private fun CalendarArrow(description: String, label: String, onClick: () -> Unit) {
  Box(Modifier.size(48.dp).semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.titleLarge, color = LocalThemeSpec.current.ink)
  }
}

@Composable
fun BrTimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
  val spec = LocalThemeSpec.current
  var hour by remember(initialMinutes) { mutableIntStateOf(initialMinutes / 60) }
  var minute by remember(initialMinutes) { mutableIntStateOf(initialMinutes % 60) }
  BrDialog("Reminder time", onDismiss, confirmText = "APPLY", onConfirm = { onSelect(hour * 60 + minute) }) {
    Text("24-hour time · tap + or − to adjust", style = MaterialTheme.typography.bodySmall, color = spec.muted)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TimeColumn("HOUR", hour, 24, Modifier.weight(1f)) { hour = it }
      Text(":", style = MaterialTheme.typography.headlineMedium, color = spec.muted)
      TimeColumn("MINUTE", minute, 60, Modifier.weight(1f)) { minute = it }
    }
  }
}

@Composable
private fun TimeColumn(label: String, value: Int, limit: Int, modifier: Modifier, onChange: (Int) -> Unit) {
  val spec = LocalThemeSpec.current
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    CalendarArrow("Increase ${label.lowercase()}", "+") { onChange((value + 1) % limit) }
    Text("%02d".format(value), style = MaterialTheme.typography.headlineLarge, color = spec.ink)
    CalendarArrow("Decrease ${label.lowercase()}", "−") { onChange((value + limit - 1) % limit) }
  }
}
