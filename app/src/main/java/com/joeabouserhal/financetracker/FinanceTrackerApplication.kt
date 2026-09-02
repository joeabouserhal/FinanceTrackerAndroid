package com.joeabouserhal.financetracker

import android.app.Application
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import com.joeabouserhal.financetracker.data.sync.SyncVersion
import com.joeabouserhal.financetracker.di.AppContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FinanceTrackerApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    SyncVersion.initialize(this)
    container = AppContainer(this)

    // Seed the guest partition once; this does not change the active owner.
    container.applicationScope.launch {
      GuestSeeder.seedGuestDefaults(container.appDatabase, GUEST_OWNER_ID)
    }

    // Background sync: periodic, network-constrained. Sign-out cancels it.
    container.syncScheduler.startPeriodic()

    // Foreground trigger: whenever the device comes online and someone is
    // signed in, drain the outbox / pull right away. The worker itself
    // re-checks the session, so a race with sign-out is harmless.
    container.applicationScope.launch {
      container.connectivityMonitor.isOnline.collectLatest { online ->
        if (online && !container.sessionManager.session.first().isGuest) {
          container.syncScheduler.requestSyncNow()
        }
      }
    }

    // Mutation trigger: every add/edit/delete calls OutboxWriter.enqueue in
    // the same Room transaction, which fires SyncRequests. Collect it and
    // request a sync immediately — deterministic, no Room-flow timing
    // involved. WorkManager coalesces bursts via APPEND_OR_REPLACE. Guest
    // mutations never enqueue, so guests never trigger.
    container.applicationScope.launch {
      com.joeabouserhal.financetracker.data.sync.SyncRequests.mutations.collectLatest {
        android.util.Log.i("SyncTrigger", "mutation queued — requesting sync")
        container.syncScheduler.requestSyncNow()
      }
    }
  }
}
