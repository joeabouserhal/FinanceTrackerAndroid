package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
  @Query("SELECT * FROM profiles WHERE owner_id = :ownerId")
  fun observe(ownerId: String): Flow<ProfileEntity?>

  @Query("SELECT * FROM profiles WHERE owner_id = :ownerId")
  suspend fun get(ownerId: String): ProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ProfileEntity)

  @Update
  suspend fun replaceFromSync(entity: ProfileEntity)

  @Query("DELETE FROM profiles WHERE owner_id = :ownerId")
  suspend fun delete(ownerId: String)
}
