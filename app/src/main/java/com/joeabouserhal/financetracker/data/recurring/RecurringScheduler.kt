package com.joeabouserhal.financetracker.data.recurring

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Schedules a single durable local wake-up; the worker computes all due rows atomically. */
class RecurringScheduler(private val context: Context, private val repository: RecurringRepository) {
  private val workManager = WorkManager.getInstance(context)

  suspend fun scheduleNext(ownerId: String) {
    val now = Instant.now(); val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val regular = repository.nextDue(ownerId, today, now, zone)
    val snoozed = repository.nextSnooze(ownerId)
    val target = listOfNotNull(regular, snoozed).minOrNull() ?: run { cancel(); return }
    val delay = Duration.between(now, target).toMillis().coerceAtLeast(0)
    val request = OneTimeWorkRequestBuilder<RecurringWorker>()
      .setInputData(androidx.work.workDataOf("timer" to true))
      .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
    // The wake-up and the processor have separate identities. A running processor
    // must not cancel itself when it schedules tomorrow's wake-up.
    workManager.enqueueUniqueWork(WAKE_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  fun requestNow() {
    workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<RecurringWorker>().build())
  }
  fun cancel() { workManager.cancelUniqueWork(WAKE_NAME) }
  companion object { const val WORK_NAME = "recurring_transactions"; private const val WAKE_NAME = "recurring_wake" }
}
