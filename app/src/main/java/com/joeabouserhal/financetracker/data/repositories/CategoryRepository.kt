package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
  private val dao: CategoryDao,
  private val db: AppDatabase,
) {
  companion object {
    /** Goal withdrawals use this dedicated category instead of "Other". */
    const val GOAL_CATEGORY_NAME = "Goal"

    /** Matches the golden GOAL transaction styling (#D4AF37). */
    const val GOAL_CATEGORY_COLOR = "#D4AF37"
  }

  fun observeByType(ownerId: String, type: TransactionType): Flow<List<CategoryEntity>> =
    dao.observeByType(ownerId, type)

  fun observeAll(ownerId: String): Flow<List<CategoryEntity>> = dao.observeAll(ownerId)

  /**
   * The custom "Goal" expense category used by goal-completion transactions.
   * Created on first use and reused afterwards (also reuses a user-created
   * category with the same name).
   */
  suspend fun ensureGoalCategory(ownerId: String): CategoryEntity {
    val existing =
      dao.getAll(ownerId).firstOrNull { it.type == TransactionType.EXPENSE && it.name == GOAL_CATEGORY_NAME }
    if (existing != null) return existing
    return add(ownerId, GOAL_CATEGORY_NAME, TransactionType.EXPENSE, GOAL_CATEGORY_COLOR)
  }

  suspend fun add(ownerId: String, name: String, type: TransactionType, color: String): CategoryEntity {
    require(name.trim().isNotBlank()) { "Category name is required" }
    require(dao.getAll(ownerId).none { it.type == type && it.name.equals(name.trim(), ignoreCase = true) }) {
      "A category named \"${name.trim()}\" already exists"
    }
    val now = java.time.Instant.now().toString()
    val entity =
      CategoryEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        name = name.trim(),
        type = type,
        color = color.ifBlank { "#77746C" },
        isDefault = false,
        createdAt = now,
        updatedAt = now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "categories", OutboxAction.INSERT, OutboxWriter.category(ownerId, entity))
    }
    return entity
  }

  suspend fun update(ownerId: String, id: String, name: String, type: TransactionType, color: String) {
    require(name.trim().isNotBlank()) { "Category name is required" }
    require(
      dao.getAll(ownerId).none { it.id != id && it.type == type && it.name.equals(name.trim(), ignoreCase = true) },
    ) {
      "A category named \"${name.trim()}\" already exists"
    }
    db.withTransaction {
      dao.update(ownerId, id, name.trim(), type, color.ifBlank { "#77746C" }, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "categories", OutboxAction.UPDATE, OutboxWriter.category(ownerId, full))
      }
    }
  }

  /**
   * Delete with the enforcement rule: the seeded "Other" category of a type is
   * undeletable, and every other category's transactions are reassigned to
   * "Other" before the category is removed. Both changes are queued.
   */
  suspend fun delete(ownerId: String, id: String) {
    val category = dao.getById(ownerId, id) ?: return
    require(!(category.isDefault && category.name == "Other")) { "The 'Other' category can't be deleted" }

    val other = dao.getDefaultOther(ownerId, category.type)
      ?: throw IllegalStateException("Missing default 'Other' category")

    db.withTransaction {
      val now = java.time.Instant.now().toString()
      val affected = db.transactionDao().getAll(ownerId).filter { it.categoryId == id }
      dao.reassignTransactions(ownerId, id, other.id, now)
      val deletedAt = java.time.Instant.now().toString()
      if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, id)
      else dao.delete(ownerId, id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
      OutboxWriter.enqueue(db, ownerId, "categories", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
      affected.forEach { tx ->
        val updated = tx.copy(categoryId = other.id, updatedAt = now)
        OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.UPDATE, OutboxWriter.transaction(ownerId, updated))
      }
    }
  }
}
