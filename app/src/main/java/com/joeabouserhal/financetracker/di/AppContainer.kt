package com.joeabouserhal.financetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.joeabouserhal.financetracker.data.auth.AuthRepository
import com.joeabouserhal.financetracker.data.auth.GoogleSignIn
import com.joeabouserhal.financetracker.data.auth.SupabaseAuthApi
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.Migrations
import com.joeabouserhal.financetracker.data.remote.SupabaseClientProvider
import com.joeabouserhal.financetracker.data.repositories.AccountRepository
import com.joeabouserhal.financetracker.data.repositories.CategoryRepository
import com.joeabouserhal.financetracker.data.repositories.CurrencyRepository
import com.joeabouserhal.financetracker.data.repositories.DashboardRepository
import com.joeabouserhal.financetracker.data.repositories.GoalRepository
import com.joeabouserhal.financetracker.data.repositories.PresetRepository
import com.joeabouserhal.financetracker.data.repositories.ProfileRepository
import com.joeabouserhal.financetracker.data.repositories.TransactionRepository
import com.joeabouserhal.financetracker.data.session.SessionManager
import com.joeabouserhal.financetracker.data.settings.SettingsRepository
import com.joeabouserhal.financetracker.data.sync.ConnectivityMonitor
import com.joeabouserhal.financetracker.data.sync.SupabaseSyncApi
import com.joeabouserhal.financetracker.data.sync.SyncEngine
import com.joeabouserhal.financetracker.data.sync.SyncScheduler
import com.joeabouserhal.financetracker.data.sync.SyncStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped manual DI container.
 *
 * We deliberately use a tiny service-locator instead of Hilt: the template is
 * on Navigation 3 (`androidx.navigation3`), which has no Hilt view-model
 * integration yet, so Hilt would add a KSP plugin and factory boilerplate
 * without buying us anything here. Everything hangs off this one object and
 * is easy to swap for Hilt later if Nav3 integration lands.
 */
class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext

  /** App settings (theme, sync prefs). Single DataStore per process. */
  val settingsDataStore: DataStore<Preferences> = appContext.settingsDataStore

  val settingsRepository: SettingsRepository = SettingsRepository(settingsDataStore)

  val appDatabase: AppDatabase =
    Room.databaseBuilder(appContext, AppDatabase::class.java, "finance_tracker.db")
      .addMigrations(*Migrations.ALL)
      .build()

  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val sessionManager: SessionManager = SessionManager(settingsDataStore, appDatabase)

  // Repositories
  val currencyRepository: CurrencyRepository = CurrencyRepository(appDatabase.currencyDao(), appDatabase)
  val accountRepository: AccountRepository =
    AccountRepository(appDatabase.accountDao(), appDatabase.transactionDao(), appDatabase)
  val categoryRepository: CategoryRepository = CategoryRepository(appDatabase.categoryDao(), appDatabase)
  val presetRepository: PresetRepository = PresetRepository(appDatabase.presetDao(), appDatabase)
  val goalRepository: GoalRepository = GoalRepository(appDatabase.goalDao(), appDatabase)
  val transactionRepository: TransactionRepository =
    TransactionRepository(appDatabase.transactionDao(), appDatabase.categoryDao(), appDatabase)
  val profileRepository: ProfileRepository = ProfileRepository(appDatabase.profileDao(), appDatabase)
  val dashboardRepository: DashboardRepository =
    DashboardRepository(
      appDatabase.currencyDao(),
      appDatabase.accountDao(),
      appDatabase.categoryDao(),
      appDatabase.transactionDao(),
    )

  // Auth (null when Supabase is not configured → guest-only mode)
  val supabaseClientProvider: SupabaseClientProvider = SupabaseClientProvider(appContext)
  val authRepository: AuthRepository =
    AuthRepository(supabaseClientProvider.client?.let { SupabaseAuthApi(it) })
  val googleSignIn: GoogleSignIn = GoogleSignIn(appContext)

  // Offline-first sync engine
  val connectivityMonitor: ConnectivityMonitor = ConnectivityMonitor(appContext)
  val syncEngine: SyncEngine =
    SyncEngine(appDatabase, sessionManager.session, SupabaseSyncApi(supabaseClientProvider.client))
  val syncScheduler: SyncScheduler = SyncScheduler(appContext)
  val syncStatusRepository: SyncStatusRepository =
    SyncStatusRepository(
      sessionManager = sessionManager,
      outboxDao = appDatabase.outboxDao(),
      syncMetaDao = appDatabase.syncMetaDao(),
      connectivityMonitor = connectivityMonitor,
      workInfos = syncScheduler.workInfos,
    )
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "settings",
)
