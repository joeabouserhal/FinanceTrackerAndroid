package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
  private val dao: TransactionDao,
  private val categoryDao: CategoryDao,
  private val db: AppDatabase,
) {
  fun observeAll(ownerId: String): Flow<List<TransactionEntity>> = dao.observeAll(ownerId)

  fun observeBetween(ownerId: String, dateFrom: String, dateTo: String): Flow<List<TransactionEntity>> =
    dao.observeBetween(ownerId, dateFrom, dateTo)

  fun observeRecent(ownerId: String, limit: Int): Flow<List<TransactionEntity>> = dao.observeRecent(ownerId, limit)

  suspend fun add(
    ownerId: String,
    type: TransactionType,
    amount: Long,
    currencyId: String,
    categoryId: String?,
    accountId: String?,
    date: String?,
    title: String?,
    notes: String?,
    presetId: String?,
  ): TransactionEntity {
    require(amount > 0) { "Amount must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    // Enforced category: fall back to the seeded "Other" of this type.
    val resolvedCategoryId = categoryId?.takeIf { it.isNotBlank() } ?: resolveOther(ownerId, type)

    val now = java.time.Instant.now().toString()
    val entity =
      TransactionEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        type = type,
        amount = amount,
        currencyId = currencyId,
        categoryId = resolvedCategoryId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString(),
        title = title?.takeIf { it.isNotBlank() },
        notes = notes?.takeIf { it.isNotBlank() },
        presetId = presetId?.takeIf { it.isNotBlank() },
        createdAt = now,
        updatedAt = now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.INSERT, OutboxWriter.transaction(ownerId, entity))
    }
    return entity
  }

  suspend fun update(
    ownerId: String,
    id: String,
    type: TransactionType,
    amount: Long,
    currencyId: String,
    categoryId: String?,
    accountId: String?,
    date: String?,
    title: String?,
    notes: String?,
    presetId: String?,
  ) {
    require(amount > 0) { "Amount must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    val resolvedCategoryId = categoryId?.takeIf { it.isNotBlank() } ?: resolveOther(ownerId, type)
    // Manual edits don't clear the "created from preset" lineage: only an
    // explicit presetId wins over the existing one.
    val existing = dao.getById(ownerId, id)
    db.withTransaction {
      dao.update(
        ownerId = ownerId,
        id = id,
        type = type,
        amount = amount,
        currencyId = currencyId,
        categoryId = resolvedCategoryId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString(),
        title = title?.takeIf { it.isNotBlank() },
        notes = notes?.takeIf { it.isNotBlank() },
        presetId = presetId?.takeIf { it.isNotBlank() } ?: existing?.presetId,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.UPDATE, OutboxWriter.transaction(ownerId, full))
      }
    }
  }

  suspend fun remove(ownerId: String, id: String) {
    db.withTransaction {
      dao.delete(ownerId, id)
      OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }

  private suspend fun resolveOther(ownerId: String, type: TransactionType): String =
    categoryDao.getDefaultOther(ownerId, type)?.id
      ?: throw IllegalStateException("No default category found for $type")
}
