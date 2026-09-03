package com.joeabouserhal.financetracker.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Entry point for WorkManager. The owner is read from the session at run time,
 * so a queued worker never syncs a stale user after sign-out — and the guest
 * partition is skipped inside [SyncEngine] itself.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val app = applicationContext as? FinanceTrackerApplication ?: return Result.success()
    return when (val outcome = app.container.syncEngine.sync()) {
      is SyncOutcome.Skipped -> if (outcome.reason == SyncOutcome.SkipReason.NO_SESSION) Result.retry() else Result.success()
      // Validation failures remain visible and are retried on manual/periodic runs.
      is SyncOutcome.Completed -> if (outcome.failedOps == 0 && !outcome.pullFailed) Result.success() else Result.retry()
    }
  }

  companion object {
    const val PERIODIC_WORK = "periodic_sync"
    const val NOW_WORK = "sync_now"
  }
}

/**
 * Wraps WorkManager: a periodic 15-minute sync with a network constraint plus
 * on-demand "sync now" triggers (sign-in, connectivity regained, manual
 * button). Sign-out cancels both.
 */
class SyncScheduler(private val context: Context) {
  private val workManager: WorkManager = WorkManager.getInstance(context)

  private val networkConstraint =
    Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

  fun startPeriodic() {
    val request =
      PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
        .setConstraints(networkConstraint)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()
    workManager.enqueueUniquePeriodicWork(
      SyncWorker.PERIODIC_WORK,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }

  fun requestSyncNow() {
    val request =
      OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(networkConstraint)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    // Never cancel an in-flight push: each request is idempotent, but keeping
    // the active worker avoids duplicate network work and ensures follow-up
    // requests run after it has finished.
    workManager.enqueueUniqueWork(SyncWorker.NOW_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
  }

  /** Sign-out: stop both periodic and pending on-demand syncs. */
  fun cancelAll() {
    workManager.cancelUniqueWork(SyncWorker.NOW_WORK)
    workManager.cancelUniqueWork(SyncWorker.PERIODIC_WORK)
  }

  /** Live work infos of both sync chains, for the "syncing…" indicator. */
  val workInfos: Flow<List<WorkInfo>> =
    combine(
      workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.NOW_WORK),
      workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.PERIODIC_WORK),
    ) { now, periodic -> now + periodic }
}
