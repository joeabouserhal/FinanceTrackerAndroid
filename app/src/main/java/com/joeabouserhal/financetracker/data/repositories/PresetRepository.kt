package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.withMutation
import com.joeabouserhal.financetracker.data.local.validateReferences
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.PresetDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PresetRepository(
  private val dao: PresetDao,
  private val db: AppDatabase,
) {
  fun observeByType(ownerId: String, type: TransactionType): Flow<List<PresetEntity>> =
    dao.observeByType(ownerId, type)

  fun observeAll(ownerId: String): Flow<List<PresetEntity>> = dao.observeAll(ownerId)

  suspend fun add(
    ownerId: String,
    name: String,
    type: TransactionType,
    defaultAmount: Long?,
    defaultCurrencyId: String?,
    defaultCategoryId: String?,
    defaultAccountId: String?,
  ): PresetEntity {
    require(name.trim().isNotBlank()) { "Preset name is required" }
    require(defaultAmount == null || defaultAmount > 0) { "Amount must be positive" }
    val now = java.time.Instant.now().toString()
    val entity =
      PresetEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        name = name.trim(),
        type = type,
        defaultAmount = defaultAmount,
        defaultCurrencyId = defaultCurrencyId,
        defaultCategoryId = defaultCategoryId,
        defaultAccountId = defaultAccountId,
        archived = false,
        createdAt = now,
        updatedAt = now,
      )
    db.withMutation {
      require(type in listOf(TransactionType.INCOME, TransactionType.EXPENSE)) { "Choose income or expense" }
      db.validateReferences(ownerId, defaultCurrencyId, defaultAccountId, defaultCategoryId, type)
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "presets", OutboxAction.INSERT, OutboxWriter.preset(ownerId, entity))
    }
    return entity
  }

  suspend fun update(
    ownerId: String,
    id: String,
    name: String,
    defaultAmount: Long?,
    defaultCurrencyId: String?,
    defaultCategoryId: String?,
    defaultAccountId: String?,
  ) {
    require(name.trim().isNotBlank()) { "Preset name is required" }
    require(defaultAmount == null || defaultAmount > 0) { "Amount must be positive" }
    db.withMutation {
      val current = dao.getById(ownerId, id)
      require(current != null && current.deletedAt == null) { "This preset no longer exists" }
      db.validateReferences(ownerId, defaultCurrencyId, defaultAccountId, defaultCategoryId, current.type, current.defaultAccountId == defaultAccountId)
      dao.update(ownerId, id, name.trim(), defaultAmount, defaultCurrencyId, defaultCategoryId, defaultAccountId, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "presets", OutboxAction.UPDATE, OutboxWriter.preset(ownerId, full))
      }
    }
  }

  suspend fun archive(ownerId: String, id: String) {
    db.withMutation {
      pauseSchedule(ownerId, id)
      dao.archive(ownerId, id, java.time.Instant.now().toString())
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "presets", OutboxAction.UPDATE, OutboxWriter.preset(ownerId, full))
      }
    }
  }

  suspend fun delete(ownerId: String, id: String) {
    db.withMutation {
      pauseSchedule(ownerId, id)
      val deletedAt = java.time.Instant.now().toString()
      if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, id)
      else dao.delete(ownerId, id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
      OutboxWriter.enqueue(db, ownerId, "presets", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }

  private suspend fun pauseSchedule(ownerId: String, presetId: String) {
    db.recurringScheduleDao().getForPreset(ownerId, presetId)?.let { schedule ->
      if (!schedule.paused) {
        val paused = schedule.copy(paused = true, updatedAt = java.time.Instant.now().toString())
        db.recurringScheduleDao().upsert(paused)
        OutboxWriter.enqueue(db, ownerId, "recurring_schedules", OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, paused))
      }
    }
  }
}
