package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
  @Query("SELECT * FROM outbox WHERE owner_id = :ownerId ORDER BY id")
  fun observeForOwner(ownerId: String): Flow<List<OutboxEntity>>

  @Query("SELECT * FROM outbox WHERE owner_id = :ownerId ORDER BY id")
  suspend fun getAllForOwner(ownerId: String): List<OutboxEntity>

  @Query("SELECT * FROM outbox WHERE owner_id = :ownerId AND table_name = :tableName ORDER BY id")
  suspend fun getAllForOwnerAndTable(ownerId: String, tableName: String): List<OutboxEntity>

  @Query("SELECT COUNT(*) FROM outbox WHERE owner_id = :ownerId")
  fun observeCountForOwner(ownerId: String): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: OutboxEntity): Long

  @Query("UPDATE outbox SET attempts = :attempts WHERE id = :id")
  suspend fun updateAttempts(id: Long, attempts: Int)

  @Query("UPDATE outbox SET attempts = attempts + 1, last_error = :message, error_kind = :kind, last_attempt_at = :at WHERE id = :id")
  suspend fun recordFailure(id: Long, message: String, kind: String, at: String)

  @Query("DELETE FROM outbox WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM outbox WHERE owner_id = :ownerId")
  suspend fun deleteForOwner(ownerId: String)
}
