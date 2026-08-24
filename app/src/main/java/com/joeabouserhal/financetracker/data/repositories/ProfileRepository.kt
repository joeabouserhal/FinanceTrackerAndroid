package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.ProfileDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Display name for the current owner, synced like everything else. */
class ProfileRepository(
  private val dao: ProfileDao,
  private val db: AppDatabase,
) {
  fun observeName(ownerId: String): Flow<String?> = dao.observe(ownerId).map { it?.name }

  fun observeProfile(ownerId: String): Flow<ProfileEntity?> = dao.observe(ownerId)

  suspend fun getName(ownerId: String): String? = dao.get(ownerId)?.name

  suspend fun setName(ownerId: String, name: String) {
    val trimmed = name.trim()
    require(trimmed.isNotBlank()) { "Name is required" }
    val existing = dao.get(ownerId)
    val now = Instant.now().toString()
    val entity =
      ProfileEntity(
        ownerId = ownerId,
        name = trimmed,
        updatedAt = now,
        createdAt = existing?.createdAt?.takeIf { it.isNotBlank() } ?: now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "profiles", OutboxAction.UPDATE, OutboxWriter.profile(ownerId, entity))
    }
  }
}
