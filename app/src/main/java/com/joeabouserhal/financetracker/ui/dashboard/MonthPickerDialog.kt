package com.joeabouserhal.financetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import java.time.YearMonth

/** Year + month picker used by the dashboard ACTIVITY section. */
@Composable
fun MonthPickerDialog(
  current: YearMonth,
  onDismiss: () -> Unit,
  onSelect: (YearMonth) -> Unit,
) {
  val spec = LocalThemeSpec.current
  var year by remember { mutableStateOf(current.year) }
  val monthNames =
    listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

  Dialog(onDismissRequest = onDismiss) {
    Column(
      Modifier
        .fillMaxWidth()
        .background(spec.surface)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("PICK MONTH", style = MaterialTheme.typography.labelLarge, color = spec.ink)
        Text("✕", style = MaterialTheme.typography.labelLarge, color = spec.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
      }

      // ------------------------------------------------------------------ YEAR
      Text("YEAR", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        val maxYear = YearMonth.now().year
        ((maxYear) downTo (maxYear - 10)).forEach { y ->
          val selected = year == y
          Box(
            Modifier
              .background(if (selected) spec.accent else spec.surfaceAlt)
              .border(1.dp, if (selected) spec.accent else spec.border)
              .clickable { year = y }
              .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              y.toString(),
              style = MaterialTheme.typography.labelMedium,
              color = if (selected) spec.onAccent else spec.ink,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
          }
        }
      }

      Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.4f)))

      // ----------------------------------------------------------------- MONTH
      Text("MONTH", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      monthNames.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          row.forEach { name ->
            val monthValue = monthNames.indexOf(name) + 1
            val selected = year == current.year && monthValue == current.monthValue
            Box(
              Modifier
                .weight(1f)
                .aspectRatio(1.6f)
                .background(if (selected) spec.accent else spec.surfaceAlt)
                .border(1.dp, if (selected) spec.accent else spec.border)
                .clickable { onSelect(YearMonth.of(year, monthValue)) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) spec.onAccent else spec.ink,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              )
            }
          }
        }
      }
    }
  }
}
