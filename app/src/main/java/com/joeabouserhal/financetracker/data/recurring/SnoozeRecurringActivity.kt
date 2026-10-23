package com.joeabouserhal.financetracker.data.recurring

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrDatePickerDialog
import com.joeabouserhal.financetracker.ui.components.BrTimePickerDialog
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Small focused activity launched from a notification; it never exposes another owner's occurrence. */
class SnoozeRecurringActivity : com.joeabouserhal.financetracker.data.security.AppLockActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val occurrenceId = intent.getStringExtra(RecurringNotifications.EXTRA_OCCURRENCE_ID) ?: run { finish(); return }
    setContent {
      val app = application as com.joeabouserhal.financetracker.FinanceTrackerApplication
      val scope = rememberCoroutineScope()
      var showDatePicker by remember { mutableStateOf(false) }
      var chosenDate by remember { mutableStateOf<LocalDate?>(null) }
      fun snooze(until: Instant) = scope.launch {
        val owner = app.container.sessionManager.session.first().ownerId
        app.container.recurringRepository.snooze(owner, occurrenceId, until)
        app.container.recurringNotifications.cancel(occurrenceId)
        app.container.recurringScheduler.scheduleNext(owner)
        finish()
      }
      FinanceTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          com.joeabouserhal.financetracker.ui.security.AppLockGate(this@SnoozeRecurringActivity) {
          BrDialog(title = "SNOOZE TRANSACTION", onDismiss = ::finish, dismissText = null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              BrButton("1 HOUR", { snooze(Instant.now().plusSeconds(3600)) }, style = BrButtonStyle.SOLID)
              BrButton("TOMORROW", {
                scope.launch {
                  val owner = app.container.sessionManager.session.first().ownerId
                  val occurrence = app.container.appDatabase.recurringOccurrenceDao().getById(owner, occurrenceId)
                  val schedule = occurrence?.let { app.container.appDatabase.recurringScheduleDao().getById(owner, it.scheduleId) }
                  val minutes = schedule?.timeMinutes ?: 9 * 60
                  app.container.recurringRepository.snooze(owner, occurrenceId, LocalDateTime.now().plusDays(1).withHour(minutes / 60).withMinute(minutes % 60).atZone(ZoneId.systemDefault()).toInstant())
                  app.container.recurringNotifications.cancel(occurrenceId)
                  app.container.recurringScheduler.scheduleNext(owner)
                  finish()
                }
              }, style = BrButtonStyle.OUTLINE)
              BrButton("CHOOSE DATE & TIME", {
                showDatePicker = true
              }, style = BrButtonStyle.OUTLINE)
            }
          }
          if (showDatePicker) BrDatePickerDialog("Snooze until", LocalDate.now(), minimumDate = LocalDate.now(),
            onDismiss = { showDatePicker = false }, onSelect = { chosenDate = it; showDatePicker = false })
          chosenDate?.let { date ->
            val now = LocalTime.now()
            BrTimePickerDialog(now.hour * 60 + now.minute, onDismiss = { chosenDate = null }, onSelect = { minutes ->
              snooze(date.atTime(minutes / 60, minutes % 60).atZone(ZoneId.systemDefault()).toInstant())
              chosenDate = null
            })
          }
          }
        }
      }
    }
  }
}
