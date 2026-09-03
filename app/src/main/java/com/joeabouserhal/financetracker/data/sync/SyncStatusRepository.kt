package com.joeabouserhal.financetracker.data.sync

import androidx.work.WorkInfo
import com.joeabouserhal.financetracker.data.local.dao.OutboxDao
import com.joeabouserhal.financetracker.data.local.dao.SyncHealthDao
import com.joeabouserhal.financetracker.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** Everything Settings/Dashboard need to render the sync status block. */
data class SyncStatus(
  val isGuest: Boolean = true,
  val isOnline: Boolean = false,
  val isSyncing: Boolean = false,
  val pendingCount: Int = 0,
  val lastSyncAt: String? = null,
  val retryingCount: Int = 0,
  val actionRequiredCount: Int = 0,
  val lastError: String? = null,
  val errorKind: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncStatusRepository(
  private val sessionManager: SessionManager,
  private val outboxDao: OutboxDao,
  private val syncHealthDao: SyncHealthDao,
  private val connectivityMonitor: ConnectivityMonitor,
  private val workInfos: Flow<List<WorkInfo>>,
) {
  fun observe(): Flow<SyncStatus> =
    sessionManager.session.flatMapLatest { session ->
      combine(
        connectivityMonitor.isOnline,
        outboxDao.observeForOwner(session.ownerId),
        syncHealthDao.observe(session.ownerId),
        workInfos,
      ) { online, pending, health, infos ->
        SyncStatus(
          isGuest = session.isGuest,
          isOnline = online,
          // Only a RUNNING worker means "syncing now". ENQUEUED work (periodic
          // waiting for its next window, or a delayed retry) is idle, not
          // syncing — otherwise the pill would never leave "Syncing…".
          isSyncing = infos.any { it.state == WorkInfo.State.RUNNING },
          pendingCount = pending.size,
          lastSyncAt = health?.lastSuccessAt,
          retryingCount = pending.count { it.errorKind == "RETRYING" },
          actionRequiredCount = pending.count { it.errorKind in setOf("ACTION_REQUIRED", "AUTH_REQUIRED") },
          lastError = pending.firstOrNull { it.lastError != null }?.lastError ?: health?.lastError,
          errorKind = pending.firstOrNull { it.errorKind != null }?.errorKind ?: health?.errorKind,
        )
      }
    }
}
