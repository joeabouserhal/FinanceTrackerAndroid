package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
  @Query("SELECT * FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL ORDER BY date DESC, created_at DESC")
  fun observeAll(ownerId: String): Flow<List<TransactionEntity>>

  @Query(
    """
    SELECT * FROM transactions
    WHERE owner_id = :ownerId AND deleted_at IS NULL AND date >= :dateFrom AND date <= :dateTo
    ORDER BY date DESC, created_at DESC
    """
  )
  fun observeBetween(ownerId: String, dateFrom: String, dateTo: String): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL ORDER BY date DESC, created_at DESC LIMIT :limit")
  fun observeRecent(ownerId: String, limit: Int): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL")
  suspend fun getAll(ownerId: String): List<TransactionEntity>

  @Query("SELECT * FROM transactions WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): TransactionEntity?

  @Query("SELECT * FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL AND goal_id = :goalId")
  suspend fun getByGoal(ownerId: String, goalId: String): List<TransactionEntity>

  @Query("SELECT COUNT(*) FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL AND account_id = :accountId")
  suspend fun countByAccount(ownerId: String, accountId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: TransactionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<TransactionEntity>)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<TransactionEntity>)

  @Update
  suspend fun replaceFromSync(entity: TransactionEntity)

  @Query(
    """
    UPDATE transactions
    SET type = :type, amount = :amount, currency_id = :currencyId, category_id = :categoryId,
        account_id = :accountId, date = :date, title = :title, notes = :notes, preset_id = :presetId,
        goal_id = :goalId, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun update(
    ownerId: String,
    id: String,
    type: TransactionType,
    amount: Long,
    currencyId: String,
    categoryId: String,
    accountId: String?,
    date: String,
    title: String?,
    notes: String?,
    presetId: String?,
    goalId: String?,
    updatedAt: String,
  )

  @Query("UPDATE transactions SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_version = :syncVersion WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String, deletedAt: String, syncVersion: String)

  @Query("DELETE FROM transactions WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)
}
