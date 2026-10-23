package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.withMutation
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.CurrencyDao
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class CurrencyRepository(
  private val dao: CurrencyDao,
  private val db: AppDatabase,
) {
  fun observeAll(ownerId: String): Flow<List<CurrencyEntity>> = dao.observeAll(ownerId)

  suspend fun getAll(ownerId: String): List<CurrencyEntity> = dao.getAll(ownerId)

  suspend fun add(
    ownerId: String,
    code: String,
    symbol: String,
    name: String,
    isDefault: Boolean = false,
    openingBalanceMinor: Long = 0,
  ): CurrencyEntity = db.withMutation {
    val normalizedCode = code.trim().uppercase()
    require(normalizedCode.isNotBlank()) { "Currency code is required" }
    require(name.trim().isNotBlank()) { "Currency name is required" }
    require(dao.getAll(ownerId).none { it.code.equals(normalizedCode, ignoreCase = true) }) {
      "A currency with code $normalizedCode already exists"
    }

    val now = java.time.Instant.now().toString()
    val entity =
      CurrencyEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        code = normalizedCode,
        symbol = symbol.ifBlank { normalizedCode },
        name = name.trim(),
        isDefault = isDefault || dao.getAll(ownerId).isEmpty(),
        createdAt = now,
        updatedAt = now,
      )
    db.withMutation {
      if (entity.isDefault) clearAndQueueDefaults(ownerId)
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "currencies", OutboxAction.INSERT, OutboxWriter.currency(ownerId, entity))

      // Every currency starts with a default "Cash" account so it is never
      // without one. Same transaction → the pair syncs atomically, and the
      // FK drain order (currencies before accounts) keeps it safe.
      val accountNow = java.time.Instant.now().toString()
      val defaultAccount =
        AccountEntity(
          id = UUID.randomUUID().toString(),
          ownerId = ownerId,
          currencyId = entity.id,
          name = "Cash",
          archived = false,
          isDefault = true,
          createdAt = accountNow,
          updatedAt = accountNow,
          openingBalanceMinor = openingBalanceMinor,
        )
      db.accountDao().upsert(defaultAccount)
      OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.INSERT, OutboxWriter.account(ownerId, defaultAccount))
    }
    entity
  }

  suspend fun update(ownerId: String, id: String, code: String, symbol: String, name: String, isDefault: Boolean) {
    val normalizedCode = code.trim().uppercase()
    require(normalizedCode.isNotBlank()) { "Currency code is required" }
    require(name.trim().isNotBlank()) { "Currency name is required" }
    require(dao.getAll(ownerId).none { it.id != id && it.code.equals(normalizedCode, ignoreCase = true) }) {
      "A currency with code $normalizedCode already exists"
    }
    db.withMutation {
      val target = dao.getById(ownerId, id)
      require(target != null && target.deletedAt == null) { "This currency no longer exists" }
      val effectiveDefault = isDefault || (target.isDefault && dao.getAll(ownerId).none { it.id != id && it.isDefault })
      if (effectiveDefault) clearAndQueueDefaults(ownerId, id)
      dao.update(ownerId, id, normalizedCode, symbol.ifBlank { normalizedCode }, name.trim(), effectiveDefault, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "currencies", OutboxAction.UPDATE, OutboxWriter.currency(ownerId, full))
      }
    }
  }

  /**
   * Deletes a currency (last one is blocked) and, when the default was
   * removed, promotes the first remaining currency. Returns the promoted
   * currency's code so the UI can tell the user, or null.
   */
  suspend fun delete(ownerId: String, id: String): String? = db.withMutation {
    val all = dao.getAll(ownerId)
    require(all.size > 1) { "You need at least one currency" }
    val target = all.firstOrNull { it.id == id } ?: return@withMutation null
    require(db.transactionDao().countByCurrency(ownerId, id) == 0) { "Can't delete this currency while transactions use it" }
    require(db.presetDao().getAll(ownerId).none { it.defaultCurrencyId == id }) { "Update presets using this currency before deleting it" }
    require(db.goalDao().getAll(ownerId).none { it.currencyId == id }) { "Update goals using this currency before deleting it" }
    var promotedCode: String? = null
    try {
      db.withMutation {
        val deletedAt = java.time.Instant.now().toString()
        db.accountDao().getAll(ownerId).filter { it.currencyId == id }.forEach { account ->
          if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) db.accountDao().delete(ownerId, account.id)
          else db.accountDao().delete(ownerId, account.id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
          OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.DELETE, OutboxWriter.deletePayload(account.id))
        }
        if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, id)
        else dao.delete(ownerId, id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
        OutboxWriter.enqueue(db, ownerId, "currencies", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
        if (target.isDefault) {
          dao.getAll(ownerId).firstOrNull()?.let { next ->
            val updated = next.copy(isDefault = true, updatedAt = java.time.Instant.now().toString())
            dao.update(ownerId, next.id, next.code, next.symbol, next.name, isDefault = true, updated.updatedAt)
            OutboxWriter.enqueue(db, ownerId, "currencies", OutboxAction.UPDATE, OutboxWriter.currency(ownerId, updated))
            promotedCode = next.code
          }
        }
      }
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
      throw IllegalStateException("Can't delete this currency while transactions use it")
    }
    promotedCode
  }

  private suspend fun clearAndQueueDefaults(ownerId: String, exceptId: String? = null) {
    val changed = dao.getAll(ownerId).filter { it.isDefault && it.id != exceptId }
    changed.forEach { previous ->
      val row = previous.copy(isDefault = false, updatedAt = java.time.Instant.now().toString())
      dao.update(ownerId, row.id, row.code, row.symbol, row.name, false, row.updatedAt)
      OutboxWriter.enqueue(db, ownerId, "currencies", OutboxAction.UPDATE, OutboxWriter.currency(ownerId, row))
    }
  }
}
