package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
  @Query("SELECT * FROM sync_meta WHERE owner_id = :ownerId AND table_name = :tableName")
  suspend fun get(ownerId: String, tableName: String): SyncMetaEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SyncMetaEntity)

  @Query("SELECT MAX(last_sync_at) FROM sync_meta WHERE owner_id = :ownerId")
  fun observeLatestForOwner(ownerId: String): Flow<String?>
}
