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
    val entity =
      AccountEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        currencyId = currencyId,
        name = name.trim(),
        archived = false,
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
      dao.update(ownerId, id, name.trim(), currencyId, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.UPDATE, OutboxWriter.account(ownerId, full))
      }
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
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.UPDATE, OutboxWriter.account(ownerId, full))
      }
    }
  }

  suspend fun restore(ownerId: String, id: String) {
    db.withTransaction {
      dao.restore(ownerId, id, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
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
      OutboxWriter.enqueue(db, ownerId, "accounts", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }

  private suspend fun hasAnotherActiveAccount(ownerId: String, target: AccountEntity): Boolean =
    dao.getAll(ownerId).any { it.id != target.id && it.currencyId == target.currencyId && !it.archived }
}
