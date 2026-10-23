package com.joeabouserhal.financetracker.data.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import kotlinx.coroutines.flow.first

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as? FinanceTrackerApplication ?: return Result.success()
    if (inputData.getBoolean("timer", false)) {
      app.container.recurringScheduler.requestNow()
      return Result.success()
    }
    val owner = app.container.sessionManager.session.first().ownerId
    return try {
      val due = app.container.recurringRepository.postDue(owner)
      if (app.container.sessionManager.session.first().ownerId == owner) {
        app.container.recurringNotifications.show(due)
        app.container.recurringScheduler.scheduleNext(owner)
      }
      Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
  }
}
