package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
  @RawQuery(observedEntities = [TransactionEntity::class])
  fun observePage(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>
  @Query("""
    SELECT accountId, currencyId, SUM(net) AS balanceMinor FROM (
      SELECT id AS accountId, currency_id AS currencyId, opening_balance AS net
      FROM accounts WHERE owner_id=:ownerId AND deleted_at IS NULL
      UNION ALL
      SELECT account_id AS accountId, currency_id AS currencyId,
        CASE WHEN type='INCOME' THEN amount ELSE -amount END AS net
      FROM transactions WHERE owner_id=:ownerId AND deleted_at IS NULL
      UNION ALL
      SELECT destination_account_id AS accountId, currency_id AS currencyId, amount AS net
      FROM transactions WHERE owner_id=:ownerId AND deleted_at IS NULL AND type='TRANSFER'
    ) GROUP BY accountId,currencyId
  """)
  fun observeAccountNets(ownerId: String): Flow<List<AccountNetRow>>

  @Query("SELECT DISTINCT currency_id FROM transactions WHERE owner_id=:ownerId AND deleted_at IS NULL")
  fun observeUsedCurrencies(ownerId: String): Flow<List<String>>

  @Query("SELECT * FROM transactions WHERE owner_id=:ownerId AND deleted_at IS NULL AND (account_id=:accountId OR destination_account_id=:accountId) ORDER BY date DESC,created_at DESC")
  fun observeForAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>>
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

  @Query("SELECT COUNT(*) FROM transactions WHERE owner_id = :ownerId AND deleted_at IS NULL AND (account_id = :accountId OR destination_account_id = :accountId)")
  suspend fun countByAccount(ownerId: String, accountId: String): Int

  @Query("SELECT COUNT(*) FROM transactions WHERE owner_id=:ownerId AND currency_id=:currencyId AND deleted_at IS NULL")
  suspend fun countByCurrency(ownerId: String, currencyId: String): Int

  @Query("SELECT * FROM transactions WHERE owner_id=:ownerId AND recurrence_occurrence_id=:occurrenceId AND recurrence_generation=:generation ORDER BY created_at, id LIMIT 1")
  suspend fun findRecurring(ownerId: String, occurrenceId: String, generation: Int): TransactionEntity?

  @Upsert
  suspend fun upsert(entity: TransactionEntity)

  @Upsert
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

data class AccountNetRow(val accountId: String?, val currencyId: String, val balanceMinor: Long)
