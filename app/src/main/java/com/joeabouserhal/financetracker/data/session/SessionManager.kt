package com.joeabouserhal.financetracker.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Owns the active data partition. The persisted key defaults to the guest
 * owner, so a fresh install lands in offline guest mode. Switching partitions
 * is just a key change — every query is scoped by the owner id.
 */
class SessionManager(
  private val dataStore: DataStore<Preferences>,
  private val db: AppDatabase,
) {
  private object Keys {
    val ACTIVE_OWNER = stringPreferencesKey("active_owner")
    val AUTH_CHOICE_COMPLETE = booleanPreferencesKey("auth_choice_complete")
  }

  val session: Flow<Session> =
    dataStore.data
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs -> Session(prefs[Keys.ACTIVE_OWNER] ?: GUEST_OWNER_ID) }

  /** True once the user picked guest or signed in at least once. */
  val authChoiceCompleted: Flow<Boolean> =
    dataStore.data
      .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
      .map { prefs -> prefs[Keys.AUTH_CHOICE_COMPLETE] ?: false }

  suspend fun completeAuthChoice() {
    dataStore.edit { it[Keys.AUTH_CHOICE_COMPLETE] = true }
  }

  /** Enter offline guest mode, seeding first-launch defaults if needed. */
  suspend fun enterGuestSession() {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    // Guest rows are never synced; drop any legacy guest queue rows so the
    // guest partition's pending count stays at zero.
    db.outboxDao().deleteForOwner(GUEST_OWNER_ID)
    dataStore.edit { it[Keys.ACTIVE_OWNER] = GUEST_OWNER_ID }
  }

  /** Switch to a signed-in user's partition. */
  suspend fun setUserSession(ownerId: String) {
    require(ownerId.isNotBlank() && ownerId != GUEST_OWNER_ID) { "invalid user owner id" }
    dataStore.edit { it[Keys.ACTIVE_OWNER] = ownerId }
  }

  /** Back to the guest partition (guest data stays untouched). */
  suspend fun signOutToGuest() {
    db.outboxDao().deleteForOwner(GUEST_OWNER_ID)
    dataStore.edit { it[Keys.ACTIVE_OWNER] = GUEST_OWNER_ID }
  }

  /** Un-does the "auth choice" so the app shows the login screen again (sign-out). */
  suspend fun resetAuthChoice() {
    dataStore.edit { it[Keys.AUTH_CHOICE_COMPLETE] = false }
  }
}
