package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
  @Query("SELECT * FROM currencies WHERE owner_id = :ownerId ORDER BY is_default DESC, code")
  fun observeAll(ownerId: String): Flow<List<CurrencyEntity>>

  @Query("SELECT * FROM currencies WHERE owner_id = :ownerId ORDER BY is_default DESC, code")
  suspend fun getAll(ownerId: String): List<CurrencyEntity>

  @Query("SELECT * FROM currencies WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): CurrencyEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CurrencyEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<CurrencyEntity>)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<CurrencyEntity>)

  @Query("UPDATE currencies SET is_default = 0 WHERE owner_id = :ownerId")
  suspend fun clearDefault(ownerId: String)

  @Query(
    """
    UPDATE currencies
    SET code = :code, symbol = :symbol, name = :name, is_default = :isDefault, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun update(ownerId: String, id: String, code: String, symbol: String, name: String, isDefault: Boolean, updatedAt: String)

  /** Pull-applied update: real UPDATE, never REPLACE. */
  @Query(
    """
    UPDATE currencies
    SET code = :code, symbol = :symbol, name = :name, is_default = :isDefault, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun updateFromSync(ownerId: String, id: String, code: String, symbol: String, name: String, isDefault: Boolean, updatedAt: String)

  @Query("DELETE FROM currencies WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)
}
