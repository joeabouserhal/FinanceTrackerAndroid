package com.joeabouserhal.financetracker.data.sync

import androidx.work.WorkInfo
import com.joeabouserhal.financetracker.data.local.dao.OutboxDao
import com.joeabouserhal.financetracker.data.local.dao.SyncMetaDao
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
)

class SyncStatusRepository(
  private val sessionManager: SessionManager,
  private val outboxDao: OutboxDao,
  private val syncMetaDao: SyncMetaDao,
  private val connectivityMonitor: ConnectivityMonitor,
  private val workInfos: Flow<List<WorkInfo>>,
) {
  fun observe(): Flow<SyncStatus> =
    sessionManager.session.flatMapLatest { session ->
      combine(
        connectivityMonitor.isOnline,
        outboxDao.observeCountForOwner(session.ownerId),
        syncMetaDao.observeLatestForOwner(session.ownerId),
        workInfos,
      ) { online, pending, lastSync, infos ->
        SyncStatus(
          isGuest = session.isGuest,
          isOnline = online,
          // Only a RUNNING worker means "syncing now". ENQUEUED work (periodic
          // waiting for its next window, or a delayed retry) is idle, not
          // syncing — otherwise the pill would never leave "Syncing…".
          isSyncing = infos.any { it.state == WorkInfo.State.RUNNING },
          pendingCount = pending,
          lastSyncAt = lastSync,
        )
      }
    }
}
