package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.GoalDao
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class GoalRepository(
  private val dao: GoalDao,
  private val db: AppDatabase,
) {
  fun observeAll(ownerId: String): Flow<List<GoalEntity>> = dao.observeAll(ownerId)

  suspend fun add(
    ownerId: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
  ): GoalEntity {
    require(name.trim().isNotBlank()) { "Goal name is required" }
    require(targetMinor > 0) { "Goal target must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    val now = java.time.Instant.now().toString()
    val entity =
      GoalEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        name = name.trim(),
        targetMinor = targetMinor,
        currencyId = currencyId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        createdAt = now,
        updatedAt = now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.INSERT, OutboxWriter.goal(ownerId, entity))
    }
    return entity
  }

  suspend fun update(
    ownerId: String,
    id: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
  ) {
    require(name.trim().isNotBlank()) { "Goal name is required" }
    require(targetMinor > 0) { "Goal target must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    db.withTransaction {
      dao.update(
        ownerId,
        id,
        name.trim(),
        targetMinor,
        currencyId,
        accountId?.takeIf { it.isNotBlank() },
        completed = false,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
    }
  }

  /** Mark a goal as completed (it moves to the Completed view). */
  suspend fun markComplete(ownerId: String, id: String) {
    val existing = dao.getById(ownerId, id) ?: return
    if (existing.completed) return
    db.withTransaction {
      dao.update(
        ownerId,
        id,
        existing.name,
        existing.targetMinor,
        existing.currencyId,
        existing.accountId,
        completed = true,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
    }
  }

  /** Undo a completed goal (accidental taps) — back to the active list. */
  suspend fun markActive(ownerId: String, id: String) {
    val existing = dao.getById(ownerId, id) ?: return
    if (!existing.completed) return
    db.withTransaction {
      dao.update(
        ownerId,
        id,
        existing.name,
        existing.targetMinor,
        existing.currencyId,
        existing.accountId,
        completed = false,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
    }
  }

  suspend fun delete(ownerId: String, id: String) {
    db.withTransaction {
      dao.delete(ownerId, id)
      OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }
}
