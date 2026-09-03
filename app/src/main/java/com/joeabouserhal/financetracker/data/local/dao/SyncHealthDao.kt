package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joeabouserhal.financetracker.data.local.entities.SyncHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHealthDao {
  @Query("SELECT * FROM sync_health WHERE owner_id=:ownerId")
  fun observe(ownerId: String): Flow<SyncHealthEntity?>

  @Query("SELECT * FROM sync_health WHERE owner_id=:ownerId")
  suspend fun get(ownerId: String): SyncHealthEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(health: SyncHealthEntity)
}
