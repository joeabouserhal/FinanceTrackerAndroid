package com.joeabouserhal.financetracker.data.recurring

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import com.joeabouserhal.financetracker.theme.FinanceTrackerTheme
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrDialog
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Small focused activity launched from a notification; it never exposes another owner's occurrence. */
class SnoozeRecurringActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val occurrenceId = intent.getStringExtra(RecurringNotifications.EXTRA_OCCURRENCE_ID) ?: run { finish(); return }
    setContent {
      val app = application as com.joeabouserhal.financetracker.FinanceTrackerApplication
      val scope = rememberCoroutineScope()
      fun snooze(until: Instant) = scope.launch {
        val owner = app.container.sessionManager.session.first().ownerId
        app.container.recurringRepository.snooze(owner, occurrenceId, until)
        app.container.recurringNotifications.cancel(occurrenceId)
        app.container.recurringScheduler.scheduleNext(owner)
        finish()
      }
      FinanceTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          BrDialog(title = "SNOOZE TRANSACTION", onDismiss = ::finish, dismissText = null) {
            Column {
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
                val now = LocalDateTime.now()
                DatePickerDialog(this@SnoozeRecurringActivity, { _, year, month, day ->
                  TimePickerDialog(this@SnoozeRecurringActivity, { _, hour, minute ->
                    snooze(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant())
                  }, now.hour, now.minute, false).show()
                }, now.year, now.monthValue - 1, now.dayOfMonth).show()
              }, style = BrButtonStyle.OUTLINE)
            }
          }
        }
      }
    }
  }
}
