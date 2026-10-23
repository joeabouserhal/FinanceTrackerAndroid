package com.joeabouserhal.financetracker.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.dao.SyncMetaDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import com.joeabouserhal.financetracker.data.local.entities.SyncMetaEntity
import com.joeabouserhal.financetracker.data.session.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import com.joeabouserhal.financetracker.data.local.entities.SyncHealthEntity

sealed interface SyncOutcome {
  enum class SkipReason { GUEST, NOT_CONFIGURED, NO_SESSION }

  data class Skipped(val reason: SkipReason) : SyncOutcome

  data class Completed(
    val pushed: Int,
    val pulled: Int,
    val failedOps: Int,
    val deadOps: Int,
    val pullFailed: Boolean,
  ) : SyncOutcome {
    val isClean: Boolean get() = failedOps == 0 && deadOps == 0 && !pullFailed
  }
}

/**
 * Offline-first sync core, worker-agnostic (unit-testable with a fake [SyncApi]).
 *
 * Flow per run:
 *  1. Guest is always skipped — its partition never touches the network.
 *  2. Drain immutable logical batches in FK order; apply canonical results and
 *     acknowledge the whole batch locally in one transaction. Transient failures
 *     back off; permanent failures remain visible but are not automatically retried.
 *  3. Pull every table with a keyset watermark over (updated_at, id) and apply
 *     rows using their hybrid-clock versions; deletion remains permanent.
 */
class SyncEngine(
  private val db: AppDatabase,
  private val session: Flow<Session>,
  private val api: SyncApi?,
) {
  private val syncMeta: SyncMetaDao = db.syncMetaDao()
  private val syncMutex = Mutex()

  companion object {
    private const val TAG = "SyncEngine"

  }

  /** Last completed run for diagnostics; user-facing health is persisted in Room. */
  val latestOutcome = kotlinx.coroutines.flow.MutableStateFlow<SyncOutcome.Completed?>(null)

  /** Periodic and foreground workers share this gate so they cannot race the outbox. */
  suspend fun sync(): SyncOutcome = syncMutex.withLock { syncOnce() }

  private suspend fun syncOnce(): SyncOutcome {
    val current = session.first()
    if (current.isGuest) return SyncOutcome.Skipped(SyncOutcome.SkipReason.GUEST)
    val syncApi = api ?: return SyncOutcome.Skipped(SyncOutcome.SkipReason.NOT_CONFIGURED)
    val priorHealth = db.syncHealthDao().get(current.ownerId)
    try {
      if (!syncApi.ensureAuthenticatedFor(current.ownerId)) {
        db.syncHealthDao().upsert((priorHealth ?: SyncHealthEntity(current.ownerId)).copy(lastError = "Sign in again to resume sync.", errorKind = "AUTH_REQUIRED"))
        return SyncOutcome.Skipped(SyncOutcome.SkipReason.NO_SESSION)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val failure = SyncFailure.from(e)
      db.syncHealthDao().upsert((priorHealth ?: SyncHealthEntity(current.ownerId)).copy(lastError = failure.message, errorKind = failure.kind))
      return SyncOutcome.Completed(0, 0, 1, 0, false).also { latestOutcome.value = it }
    }

    var pushed = 0
    var failedOps = 0
    var deadOps = 0
    var conflictsResolved = 0
    var firstFailure: SyncFailure? = null
    val pending = db.outboxDao().getAllForOwner(current.ownerId)
    val ranks = SyncTables.ALL.mapIndexed { index, spec -> spec.name to index }.toMap()
    val batches = pending.groupBy { it.batchId }.values
      .sortedBy { batch -> batch.minOf { ranks[it.tableName] ?: Int.MAX_VALUE } }
    for (batch in batches) {
      currentCoroutineContext().ensureActive()
      if (session.first() != current) return SyncOutcome.Skipped(SyncOutcome.SkipReason.NO_SESSION)
      if (batch.any { it.state == "ACTION_REQUIRED" }) { deadOps += batch.size; continue }
      if (batch.any { it.nextAttemptAt?.let { at -> Instant.parse(at).isAfter(Instant.now()) } == true }) {
        failedOps += batch.size
        break
      }
      val ordered = batch.sortedWith(compareBy({ ranks[it.tableName] ?: Int.MAX_VALUE }, { it.batchIndex }, { it.id }))
      try {
        val results = syncApi.applyBatch(ordered)
        check(results.size == ordered.size) { "Incomplete batch acknowledgment" }
        db.withTransaction {
          ordered.zip(results).forEach { (op, result) ->
            val spec = SyncTables.ALL.first { it.name == op.tableName }
            val payload = Json.parseToJsonElement(op.payloadJson).jsonObject
            if (result?.row != null) {
              if (result.status == "superseded" && spec.name != "profiles") {
                db.openHelper.writableDatabase.execSQL(
                  "UPDATE `${spec.name}` SET sync_version='' WHERE owner_id=? AND id=? AND sync_version=?",
                  arrayOf(current.ownerId, payload.req(spec.keyColumn), payload.req("sync_version")),
                )
              }
              applyRows(current.ownerId, spec.name, listOf(result.row))
            }
            if ((result?.row != null && result.row[spec.keyColumn] != payload[spec.keyColumn] || result?.row == null && result?.status in setOf("deleted", "superseded")) && spec.name != "profiles") {
              // Do not hide a later local edit queued while this batch was in flight.
              db.openHelper.writableDatabase.execSQL(
                "UPDATE `${spec.name}` SET deleted_at=coalesce(deleted_at,?) WHERE owner_id=? AND id=? AND sync_version=?",
                arrayOf(Instant.now().toString(), current.ownerId, payload.req(spec.keyColumn), payload.req("sync_version")),
              )
            }
            db.outboxDao().deleteById(op.id)
          }
        }
        pushed += ordered.size
        conflictsResolved += results.count { it?.status in setOf("stale", "superseded") }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        val failure = SyncFailure.from(e)
        db.withTransaction {
          val code = (e as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.code
          ordered.forEach { op ->
            val kind = if (code == "23503" && op.attempts >= 2) "ACTION_REQUIRED" else failure.kind
            db.outboxDao().recordFailure(op.id, "${op.tableName}: ${failure.message}", kind, Instant.now().toString())
            val delay = (30L * (1L shl op.attempts.coerceAtMost(10))).coerceAtMost(21_600)
            db.outboxDao().scheduleRetry(op.id, code, if (kind == "RETRYING") Instant.now().plusSeconds(delay).toString() else null)
          }
        }
        if (failure.kind == "RETRYING") failedOps += ordered.size else deadOps += ordered.size
        if (firstFailure == null) firstFailure = failure
        Log.w(TAG, "Push batch failed: ${failure.kind}", e)
        // Later actions may depend on this batch; preserve causality until it retries.
        break
      }
    }
    var pulled = 0
    var pullFailed = false
    for (spec in SyncTables.ALL) {
      currentCoroutineContext().ensureActive()
      if (session.first() != current) return SyncOutcome.Skipped(SyncOutcome.SkipReason.NO_SESSION)
      try {
        pulled += pullTable(syncApi, current.ownerId, spec)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        pullFailed = true
        if (firstFailure == null) firstFailure = SyncFailure.from(e)
        Log.w(TAG, "pull ${spec.name} failed: ${e.message}", e)
      }
    }

    Log.i(TAG, "sync done: pushed=$pushed pulled=$pulled failedOps=$failedOps deadOps=$deadOps pullFailed=$pullFailed")
    db.syncHealthDao().upsert(
      SyncHealthEntity(current.ownerId, if (firstFailure == null && deadOps == 0 && failedOps == 0) Instant.now().toString() else priorHealth?.lastSuccessAt,
        firstFailure?.message, firstFailure?.kind,
        if (pushed > 0) Instant.now().toString() else priorHealth?.lastPushAt,
        if (!pullFailed) Instant.now().toString() else priorHealth?.lastPullAt,
        (priorHealth?.conflictsResolved ?: 0) + conflictsResolved),
    )
    return SyncOutcome.Completed(pushed, pulled, failedOps, deadOps, pullFailed).also { latestOutcome.value = it }
  }

  /** Returns the number of rows pulled. Advances the watermark only on success. */
  private suspend fun pullTable(api: SyncApi, ownerId: String, spec: SyncTables.Spec): Int {
    var meta = syncMeta.get(ownerId, spec.name)
    var cursorTs = meta?.lastSyncAt
    var cursorId = meta?.lastSyncId
    var pulled = 0

    while (true) {
      val page = api.pullRows(spec.name, ownerId, cursorTs, cursorId, SyncApi.DEFAULT_PAGE_SIZE, spec.keyColumn)
      if (page.isEmpty()) break
      val last = page.last()
      cursorTs = last.req("updated_at")
      cursorId = last.req(spec.keyColumn)
      // Data and its cursor commit together, one page at a time.
      db.withTransaction {
        applyRows(ownerId, spec.name, page)
        syncMeta.upsert(SyncMetaEntity(ownerId, spec.name, cursorTs, cursorId))
      }
      pulled += page.size
      if (page.size < SyncApi.DEFAULT_PAGE_SIZE) break
    }

    return pulled
  }

  private suspend fun applyRows(ownerId: String, table: String, rows: List<JsonObject>) {
    require(rows.all { it.req("user_id") == ownerId }) { "Sync row owner mismatch" }
    rows.forEach { row -> (row["sync_version"] as? JsonPrimitive)?.contentOrNull?.let(SyncVersion::observe) }
    when (table) {
      "currencies" -> rows.forEach { row -> applyCurrency(ownerId, SyncMappers.currency(row)) }
      "categories" -> rows.forEach { row -> applyCategory(ownerId, SyncMappers.category(row)) }
      "accounts" -> rows.forEach { row -> applyAccount(ownerId, SyncMappers.account(row)) }
      "presets" -> rows.forEach { row -> applyPreset(ownerId, SyncMappers.preset(row)) }
      "recurring_schedules" -> rows.forEach { row -> applyRecurringSchedule(ownerId, SyncMappers.recurringSchedule(row)) }
      "recurring_occurrences" -> rows.forEach { row -> applyRecurringOccurrence(ownerId, SyncMappers.recurringOccurrence(row)) }
      "goals" -> rows.forEach { row -> applyGoal(ownerId, SyncMappers.goal(row)) }
      "transactions" -> rows.forEach { row -> applyTransaction(ownerId, SyncMappers.transaction(row)) }
      "profiles" -> rows.forEach { row -> applyProfile(ownerId, SyncMappers.profile(row)) }
      else -> throw IllegalArgumentException("Unknown sync table '$table'")
    }
  }

  private suspend fun applyCurrency(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity) {
    val dao = db.currencyDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private suspend fun applyCategory(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.CategoryEntity) {
    val dao = db.categoryDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private suspend fun applyAccount(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.AccountEntity) {
    val dao = db.accountDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> {
        dao.insertAll(listOf(remote))
        if (remote.isDefault) dao.clearDefaultExceptForCurrency(ownerId, remote.currencyId, remote.id)
      }
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> {
        dao.replaceFromSync(remote)
        if (remote.isDefault) dao.clearDefaultExceptForCurrency(ownerId, remote.currencyId, remote.id)
      }
    }
  }

  private suspend fun applyPreset(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.PresetEntity) {
    val dao = db.presetDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private suspend fun applyGoal(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.GoalEntity) {
    val dao = db.goalDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private suspend fun applyRecurringSchedule(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity) {
    val dao = db.recurringScheduleDao(); val local = dao.getById(ownerId, remote.id)
    when { local == null -> dao.insertIgnore(remote); remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote) }
  }

  private suspend fun applyRecurringOccurrence(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceEntity) {
    val dao = db.recurringOccurrenceDao(); val local = dao.getById(ownerId, remote.id)
    when { local == null -> dao.insertIgnore(remote); remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote) }
  }

  private suspend fun applyTransaction(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.TransactionEntity) {
    val dao = db.transactionDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      remote.deletedAt != null || SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private suspend fun applyProfile(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.ProfileEntity) {
    val dao = db.profileDao()
    val local = dao.get(ownerId)
    when {
      local == null -> dao.upsert(remote)
      SyncMappers.remoteAtLeastAsNew(local.syncVersion, remote.syncVersion) -> dao.replaceFromSync(remote)
    }
  }

  private fun JsonObject.req(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull
      ?: throw IllegalArgumentException("Pulled row for table is missing '$key'")
}
