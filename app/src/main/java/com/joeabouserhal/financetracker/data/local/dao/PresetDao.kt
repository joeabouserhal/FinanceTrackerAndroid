package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
  @Query("SELECT * FROM presets WHERE owner_id = :ownerId AND archived = 0 AND type = :type ORDER BY name")
  fun observeByType(ownerId: String, type: TransactionType): Flow<List<PresetEntity>>

  @Query("SELECT * FROM presets WHERE owner_id = :ownerId AND archived = 0 ORDER BY type, name")
  fun observeAll(ownerId: String): Flow<List<PresetEntity>>

  @Query("SELECT * FROM presets WHERE owner_id = :ownerId")
  suspend fun getAll(ownerId: String): List<PresetEntity>

  @Query("SELECT * FROM presets WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): PresetEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PresetEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<PresetEntity>)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<PresetEntity>)

  @Query(
    """
    UPDATE presets
    SET name = :name, default_amount = :defaultAmount, default_currency_id = :defaultCurrencyId,
        default_category_id = :defaultCategoryId, default_account_id = :defaultAccountId, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun update(
    ownerId: String,
    id: String,
    name: String,
    defaultAmount: Long?,
    defaultCurrencyId: String?,
    defaultCategoryId: String?,
    defaultAccountId: String?,
    updatedAt: String,
  )

  @Query("UPDATE presets SET archived = 1, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun archive(ownerId: String, id: String, updatedAt: String)

  /** Pull-applied update: real UPDATE, never REPLACE. */
  @Query(
    """
    UPDATE presets
    SET name = :name, type = :type, default_amount = :defaultAmount, default_currency_id = :defaultCurrencyId,
        default_category_id = :defaultCategoryId, default_account_id = :defaultAccountId, archived = :archived,
        updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun updateFromSync(
    ownerId: String,
    id: String,
    name: String,
    type: TransactionType,
    defaultAmount: Long?,
    defaultCurrencyId: String?,
    defaultCategoryId: String?,
    defaultAccountId: String?,
    archived: Boolean,
    updatedAt: String,
  )

  @Query("DELETE FROM presets WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)
}
