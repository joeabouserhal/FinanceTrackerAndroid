package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
  @Query("SELECT * FROM goals WHERE owner_id = :ownerId ORDER BY name")
  fun observeAll(ownerId: String): Flow<List<GoalEntity>>

  @Query("SELECT * FROM goals WHERE owner_id = :ownerId")
  suspend fun getAll(ownerId: String): List<GoalEntity>

  @Query("SELECT * FROM goals WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): GoalEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: GoalEntity)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<GoalEntity>)

  @Query(
    """
    UPDATE goals
    SET name = :name, target_minor = :targetMinor, currency_id = :currencyId,
        account_id = :accountId, completed = :completed, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun update(
    ownerId: String,
    id: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
    completed: Boolean,
    updatedAt: String,
  )

  /** Pull-applied update: real UPDATE, never REPLACE. */
  @Query(
    """
    UPDATE goals
    SET name = :name, target_minor = :targetMinor, currency_id = :currencyId,
        account_id = :accountId, completed = :completed, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun updateFromSync(
    ownerId: String,
    id: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
    completed: Boolean,
    updatedAt: String,
  )

  @Query("DELETE FROM goals WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)
}
