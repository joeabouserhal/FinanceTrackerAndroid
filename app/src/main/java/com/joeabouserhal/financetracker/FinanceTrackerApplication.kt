package com.joeabouserhal.financetracker

import android.app.Application
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import com.joeabouserhal.financetracker.data.sync.SyncVersion
import com.joeabouserhal.financetracker.di.AppContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
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

    // Observe committed outbox changes. A signal inside a transaction could
    // start a worker before commit, and KEEP then dropped follow-up requests.
    // Only new IDs trigger work: attempt/error updates cannot make a retry loop.
    container.applicationScope.launch {
      var previousIds = emptySet<Long>()
      container.sessionManager.session.flatMapLatest { active ->
        container.appDatabase.outboxDao().observeForOwner(active.ownerId)
          .map { ops -> if (active.isGuest) emptySet<Long>() else ops.map { it.id }.toSet() }
      }.distinctUntilChanged().debounce(250).collectLatest { ids ->
        if (ids.any { it !in previousIds }) container.syncScheduler.requestSyncNow()
        previousIds = ids
      }
    }
  }
}
