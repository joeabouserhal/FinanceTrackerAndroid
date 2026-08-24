package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.AccountDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class AccountRepository(
  private val dao: AccountDao,
  private val transactionDao: TransactionDao,
  private val db: AppDatabase,
) {
  fun observeActive(ownerId: String): Flow<List<AccountEntity>> = dao.observeActive(ownerId)

  fun observeArchived(ownerId: String): Flow<List<AccountEntity>> = dao.observeArchived(ownerId)

  fun observeByCurrency(ownerId: String, currencyId: String): Flow<List<AccountEntity>> =
    dao.observeByCurrency(ownerId, currencyId)

  suspend fun add(ownerId: String, currencyId: String, name: String): AccountEntity {
    require(name.trim().isNotBlank()) { "Account name is required" }
    val now = java.time.Instant.now().toString()
    // The first active account of a currency becomes its default.
    val hasActive = dao.getAll(ownerId).any { it.currencyId == currencyId && !it.archived }
    val entity =
      AccountEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        currencyId = currencyId,
        name = name.trim(),
        archived = false,
        isDefault = !hasActive,
        createdAt = now,
        updatedAt = now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.INSERT, OutboxWriter.account(ownerId, entity))
    }
    return entity
  }

  suspend fun update(ownerId: String, id: String, name: String, currencyId: String) {
    require(name.trim().isNotBlank()) { "Account name is required" }
    db.withTransaction {
      val target = dao.getById(ownerId, id)
      val now = java.time.Instant.now().toString()
      dao.update(ownerId, id, name.trim(), currencyId, now)
      // Keep the one-default-per-currency invariant when moving currencies.
      if (target != null && target.currencyId != currencyId) {
        if (target.isDefault) {
          promoteFirstActive(ownerId, target.currencyId, exceptId = target.id)
        }
        val targetCurrencyHasDefault = dao.getAll(ownerId).any { it.currencyId == currencyId && !it.archived && it.isDefault }
        if (!targetCurrencyHasDefault) {
          dao.setDefault(ownerId, id, now)
        }
      }
      enqueueChanged(ownerId, dao.getAll(ownerId).filter { it.id == id || it.currencyId == target?.currencyId || it.currencyId == currencyId })
    }
  }

  /** Make [id] the default account of its currency (single default enforced). */
  suspend fun setDefault(ownerId: String, id: String) {
    val target = dao.getById(ownerId, id) ?: return
    require(!target.archived) { "Can't set an archived account as default" }
    db.withTransaction {
      val now = java.time.Instant.now().toString()
      dao.clearDefaultForCurrency(ownerId, target.currencyId, now)
      dao.setDefault(ownerId, id, now)
      enqueueChanged(ownerId, dao.getAll(ownerId).filter { it.currencyId == target.currencyId })
    }
  }

  suspend fun archive(ownerId: String, id: String) {
    val target = dao.getById(ownerId, id) ?: return
    if (target.archived) return
    // A currency must always have at least one active account.
    require(hasAnotherActiveAccount(ownerId, target)) {
      "Each currency needs at least one account — add another account before hiding this one"
    }
    db.withTransaction {
      dao.archive(ownerId, id, java.time.Instant.now().toString())
      if (target.isDefault) promoteFirstActive(ownerId, target.currencyId, exceptId = target.id)
      enqueueChanged(ownerId, dao.getAll(ownerId).filter { it.currencyId == target.currencyId })
    }
  }

  suspend fun restore(ownerId: String, id: String) {
    db.withTransaction {
      dao.restore(ownerId, id, java.time.Instant.now().toString())
      val restored = dao.getById(ownerId, id)
      // If its currency lost its default while this was archived, retake it.
      if (restored != null && dao.getAll(ownerId).none { it.currencyId == restored.currencyId && !it.archived && it.isDefault }) {
        dao.setDefault(ownerId, id, restored.updatedAt)
      }
      restored?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.UPDATE, OutboxWriter.account(ownerId, full))
      }
    }
  }

  /** Delete, but refuse while transactions reference it or it's the currency's last active account. */
  suspend fun delete(ownerId: String, id: String) {
    val target = dao.getById(ownerId, id) ?: return
    val inUse = transactionDao.countByAccount(ownerId, id)
    require(inUse == 0) { "This account has transactions — archive it instead of deleting" }
    require(hasAnotherActiveAccount(ownerId, target)) {
      "Each currency needs at least one account — add another account before deleting this one"
    }
    db.withTransaction {
      dao.delete(ownerId, id)
      if (target.isDefault) promoteFirstActive(ownerId, target.currencyId, exceptId = target.id)
      OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }

  private suspend fun hasAnotherActiveAccount(ownerId: String, target: AccountEntity): Boolean =
    dao.getAll(ownerId).any { it.id != target.id && it.currencyId == target.currencyId && !it.archived }

  /** Promote the first remaining active account of a currency to default. */
  private suspend fun promoteFirstActive(ownerId: String, currencyId: String, exceptId: String) {
    val candidate = dao.getAll(ownerId).firstOrNull { it.id != exceptId && it.currencyId == currencyId && !it.archived }
    if (candidate != null) {
      dao.setDefault(ownerId, candidate.id, java.time.Instant.now().toString())
    }
  }

  /** Push UPDATE ops for every changed row so remote defaults stay in sync. */
  private suspend fun enqueueChanged(ownerId: String, rows: List<AccountEntity>) {
    rows.distinctBy { it.id }.forEach { row ->
      OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.UPDATE, OutboxWriter.account(ownerId, row))
    }
  }
}
