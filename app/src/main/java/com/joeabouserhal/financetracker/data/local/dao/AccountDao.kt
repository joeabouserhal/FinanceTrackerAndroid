package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND deleted_at IS NULL AND archived = 0 ORDER BY name")
  fun observeActive(ownerId: String): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND deleted_at IS NULL AND archived = 1 ORDER BY name")
  fun observeArchived(ownerId: String): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND deleted_at IS NULL ORDER BY name")
  fun observeAll(ownerId: String): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND deleted_at IS NULL AND currency_id = :currencyId AND archived = 0 ORDER BY name")
  fun observeByCurrency(ownerId: String, currencyId: String): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND deleted_at IS NULL")
  suspend fun getAll(ownerId: String): List<AccountEntity>

  @Query("SELECT * FROM accounts WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): AccountEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: AccountEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<AccountEntity>)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<AccountEntity>)

  @Update
  suspend fun replaceFromSync(entity: AccountEntity)

  @Query("UPDATE accounts SET name = :name, currency_id = :currencyId, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun update(ownerId: String, id: String, name: String, currencyId: String, updatedAt: String)

  @Query("UPDATE accounts SET archived = 1, is_default = 0, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun archive(ownerId: String, id: String, updatedAt: String)

  @Query("UPDATE accounts SET archived = 0, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun restore(ownerId: String, id: String, updatedAt: String)

  /** Pull-applied update: real UPDATE, never REPLACE. */
  @Query(
    """
    UPDATE accounts
    SET currency_id = :currencyId, name = :name, archived = :archived, is_default = :isDefault, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun updateFromSync(ownerId: String, id: String, currencyId: String, name: String, archived: Boolean, isDefault: Boolean, updatedAt: String)

  @Query("UPDATE accounts SET is_default = 0, updated_at = :updatedAt WHERE owner_id = :ownerId AND currency_id = :currencyId AND is_default = 1")
  suspend fun clearDefaultForCurrency(ownerId: String, currencyId: String, updatedAt: String)

  /** Sync-side invariant fix: ensure exactly one default per currency. */
  @Query("UPDATE accounts SET is_default = 0 WHERE owner_id = :ownerId AND currency_id = :currencyId AND is_default = 1 AND id != :exceptId")
  suspend fun clearDefaultExceptForCurrency(ownerId: String, currencyId: String, exceptId: String)

  @Query("UPDATE accounts SET is_default = 1, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun setDefault(ownerId: String, id: String, updatedAt: String)

  @Query("UPDATE accounts SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_version = :syncVersion WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String, deletedAt: String, syncVersion: String)

  @Query("DELETE FROM accounts WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)
}
