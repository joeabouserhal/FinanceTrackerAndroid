package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories WHERE owner_id = :ownerId AND deleted_at IS NULL AND type = :type ORDER BY is_default DESC, name")
  fun observeByType(ownerId: String, type: TransactionType): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories WHERE owner_id = :ownerId AND deleted_at IS NULL ORDER BY type, name")
  fun observeAll(ownerId: String): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories WHERE owner_id = :ownerId AND deleted_at IS NULL")
  suspend fun getAll(ownerId: String): List<CategoryEntity>

  @Query("SELECT * FROM categories WHERE owner_id = :ownerId AND id = :id")
  suspend fun getById(ownerId: String, id: String): CategoryEntity?

  @Query(
    """
    SELECT * FROM categories
    WHERE owner_id = :ownerId AND deleted_at IS NULL AND type = :type AND is_default = 1 AND name = 'Other'
    LIMIT 1
    """
  )
  suspend fun getDefaultOther(ownerId: String, type: TransactionType): CategoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CategoryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<CategoryEntity>)

  /** Sync pull inserts: never replace (REPLACE cascades to child tables). */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(entities: List<CategoryEntity>)

  @Update
  suspend fun replaceFromSync(entity: CategoryEntity)

  @Query("UPDATE categories SET name = :name, type = :type, color = :color, updated_at = :updatedAt WHERE owner_id = :ownerId AND id = :id")
  suspend fun update(ownerId: String, id: String, name: String, type: TransactionType, color: String, updatedAt: String)

  /** Pull-applied update: real UPDATE, never REPLACE. */
  @Query(
    """
    UPDATE categories
    SET name = :name, type = :type, color = :color, is_default = :isDefault, updated_at = :updatedAt
    WHERE owner_id = :ownerId AND id = :id
    """
  )
  suspend fun updateFromSync(ownerId: String, id: String, name: String, type: TransactionType, color: String, isDefault: Boolean, updatedAt: String)

  @Query("UPDATE categories SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_version = :syncVersion WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String, deletedAt: String, syncVersion: String)

  @Query("DELETE FROM categories WHERE owner_id = :ownerId AND id = :id")
  suspend fun delete(ownerId: String, id: String)

  @Query(
    "UPDATE transactions SET category_id = :newCategoryId, updated_at = :updatedAt WHERE owner_id = :ownerId AND category_id = :oldCategoryId"
  )
  suspend fun reassignTransactions(ownerId: String, oldCategoryId: String, newCategoryId: String, updatedAt: String)
}
