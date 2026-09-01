package com.joeabouserhal.financetracker.data.sync

import android.util.Log
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.dao.SyncMetaDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import com.joeabouserhal.financetracker.data.local.entities.SyncMetaEntity
import com.joeabouserhal.financetracker.data.session.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
    val isClean: Boolean get() = failedOps == 0 && !pullFailed
  }
}

/**
 * Offline-first sync core, worker-agnostic (unit-testable with a fake [SyncApi]).
 *
 * Flow per run:
 *  1. Guest is always skipped — its partition never touches the network.
 *  2. Drain the outbox in FK order (parents first), one idempotent op at a
 *     time; a successful op is removed from the queue, a failed op keeps its
 *     place and gets its attempt counter bumped.
 *  3. Pull every table with a keyset watermark over (updated_at, id) and apply
 *     rows with last-write-wins (server timestamps are authoritative).
 */
class SyncEngine(
  private val db: AppDatabase,
  private val session: Flow<Session>,
  private val api: SyncApi?,
) {
  private val syncMeta: SyncMetaDao = db.syncMetaDao()

  companion object {
    private const val TAG = "SyncEngine"

    /** Permanently failing ops stop being retried after this many attempts. */
    const val MAX_PUSH_ATTEMPTS = 10
  }

  /** Last completed sync outcome — surfaced in Options as sync health. */
  val latestOutcome = kotlinx.coroutines.flow.MutableStateFlow<SyncOutcome.Completed?>(null)

  suspend fun sync(): SyncOutcome {
    val current = session.first()
    if (current.isGuest) return SyncOutcome.Skipped(SyncOutcome.SkipReason.GUEST)
    val syncApi = api ?: return SyncOutcome.Skipped(SyncOutcome.SkipReason.NOT_CONFIGURED)
    if (!syncApi.ensureAuthenticated()) return SyncOutcome.Skipped(SyncOutcome.SkipReason.NO_SESSION)

    var pushed = 0
    var failedOps = 0
    var deadOps = 0
    for (spec in SyncTables.ALL) {
      for (op in db.outboxDao().getAllForOwnerAndTable(current.ownerId, spec.name)) {
        if (op.attempts >= MAX_PUSH_ATTEMPTS) {
          // Permanently failing op (bad FK, server-side rejection, …): keep
          // it locally so no data is lost, but stop retrying it forever.
          deadOps++
          Log.w(TAG, "op ${op.id} (${spec.name}/${op.action}) given up after ${op.attempts} attempts — kept locally")
          continue
        }
        try {
          pushOp(syncApi, op, spec)
          db.outboxDao().deleteById(op.id)
          pushed++
        } catch (e: Exception) {
          db.outboxDao().updateAttempts(op.id, op.attempts + 1)
          failedOps++
          Log.w(TAG, "push op ${op.id} (${spec.name}/${op.action}) failed: ${e.message}", e)
        }
      }
    }

    var pulled = 0
    var pullFailed = false
    for (spec in SyncTables.ALL) {
      try {
        pulled += pullTable(syncApi, current.ownerId, spec)
      } catch (e: Exception) {
        pullFailed = true
        Log.w(TAG, "pull ${spec.name} failed: ${e.message}", e)
      }
    }

    Log.i(TAG, "sync done: pushed=$pushed pulled=$pulled failedOps=$failedOps deadOps=$deadOps pullFailed=$pullFailed")
    return SyncOutcome.Completed(pushed, pulled, failedOps, deadOps, pullFailed).also { latestOutcome.value = it }
  }

  private suspend fun pushOp(api: SyncApi, op: OutboxEntity, spec: SyncTables.Spec) {
    val payload = Json.parseToJsonElement(op.payloadJson).jsonObject
    when (op.action) {
      OutboxAction.INSERT, OutboxAction.UPDATE -> api.upsert(op.tableName, payload, spec.conflictColumn)
      OutboxAction.DELETE -> {
        val id =
          (payload[spec.keyColumn] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("DELETE op for ${spec.name} has no ${spec.keyColumn}")
        api.deleteById(op.tableName, spec.keyColumn, id)
      }
    }
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
      applyRows(ownerId, spec.name, page)
      pulled += page.size
      val last = page.last()
      cursorTs = last.req("updated_at")
      cursorId = last.req(spec.keyColumn)
      if (page.size < SyncApi.DEFAULT_PAGE_SIZE) break
    }

    if (cursorTs != null) {
      syncMeta.upsert(SyncMetaEntity(ownerId = ownerId, tableName = spec.name, lastSyncAt = cursorTs, lastSyncId = cursorId))
    }
    return pulled
  }

  private suspend fun applyRows(ownerId: String, table: String, rows: List<JsonObject>) {
    when (table) {
      "currencies" -> rows.forEach { row -> safe { applyCurrency(ownerId, SyncMappers.currency(row)) } }
      "categories" -> rows.forEach { row -> safe { applyCategory(ownerId, SyncMappers.category(row)) } }
      "accounts" -> rows.forEach { row -> safe { applyAccount(ownerId, SyncMappers.account(row)) } }
      "presets" -> rows.forEach { row -> safe { applyPreset(ownerId, SyncMappers.preset(row)) } }
      "goals" -> rows.forEach { row -> safe { applyGoal(ownerId, SyncMappers.goal(row)) } }
      "transactions" -> rows.forEach { row -> safe { applyTransaction(ownerId, SyncMappers.transaction(row)) } }
      "profiles" -> rows.forEach { row -> safe { applyProfile(ownerId, SyncMappers.profile(row)) } }
      else -> throw IllegalArgumentException("Unknown sync table '$table'")
    }
  }

  private suspend fun applyCurrency(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity) {
    val dao = db.currencyDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) ->
        dao.updateFromSync(ownerId, remote.id, remote.code, remote.symbol, remote.name, remote.isDefault, remote.updatedAt)
    }
  }

  private suspend fun applyCategory(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.CategoryEntity) {
    val dao = db.categoryDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) ->
        dao.updateFromSync(ownerId, remote.id, remote.name, remote.type, remote.color, remote.isDefault, remote.updatedAt)
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
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) -> {
        dao.updateFromSync(ownerId, remote.id, remote.currencyId, remote.name, remote.archived, remote.isDefault, remote.updatedAt)
        if (remote.isDefault) dao.clearDefaultExceptForCurrency(ownerId, remote.currencyId, remote.id)
      }
    }
  }

  private suspend fun applyPreset(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.PresetEntity) {
    val dao = db.presetDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) ->
        dao.updateFromSync(
          ownerId,
          remote.id,
          remote.name,
          remote.type,
          remote.defaultAmount,
          remote.defaultCurrencyId,
          remote.defaultCategoryId,
          remote.defaultAccountId,
          remote.archived,
          remote.updatedAt,
        )
    }
  }

  private suspend fun applyGoal(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.GoalEntity) {
    val dao = db.goalDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) ->
        dao.updateFromSync(
          ownerId,
          remote.id,
          remote.name,
          remote.targetMinor,
          remote.currencyId,
          remote.accountId,
          remote.completed,
          remote.updatedAt,
        )
    }
  }

  private suspend fun applyTransaction(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.TransactionEntity) {
    val dao = db.transactionDao()
    val local = dao.getById(ownerId, remote.id)
    when {
      local == null -> dao.insertAll(listOf(remote))
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) ->
        dao.update(
          ownerId,
          remote.id,
          remote.type,
          remote.amount,
          remote.currencyId,
          remote.categoryId,
          remote.accountId,
          remote.date,
          remote.title,
          remote.notes,
          remote.presetId,
          remote.goalId,
          remote.updatedAt,
        )
    }
  }

  private suspend fun applyProfile(ownerId: String, remote: com.joeabouserhal.financetracker.data.local.entities.ProfileEntity) {
    val dao = db.profileDao()
    val local = dao.get(ownerId)
    when {
      local == null -> dao.upsert(remote)
      SyncMappers.remoteAtLeastAsNew(local.updatedAt, remote.updatedAt) -> dao.upsert(remote)
    }
  }

  private suspend inline fun safe(block: suspend () -> Unit) {
    try {
      block()
    } catch (_: Exception) {
      // Skip the malformed/FK-broken row this run; it stays behind the
      // watermark and is retried on the next sync.
    }
  }

  private fun JsonObject.req(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull
      ?: throw IllegalArgumentException("Pulled row for table is missing '$key'")
}
