package com.joeabouserhal.financetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceEntity
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringScheduleDao {
  @Query("SELECT through_date FROM recurring_progress WHERE owner_id=:ownerId AND schedule_id=:scheduleId")
  suspend fun processedThrough(ownerId: String, scheduleId: String): String?

  @Upsert suspend fun saveProgress(progress: com.joeabouserhal.financetracker.data.local.entities.RecurringProgressEntity)
  @Query("SELECT * FROM recurring_schedules WHERE owner_id=:ownerId AND deleted_at IS NULL ORDER BY paused, start_date, created_at")
  fun observeAll(ownerId: String): Flow<List<RecurringScheduleEntity>>

  @Query("SELECT * FROM recurring_schedules WHERE owner_id=:ownerId AND deleted_at IS NULL")
  suspend fun getActiveRows(ownerId: String): List<RecurringScheduleEntity>

  @Query("SELECT * FROM recurring_schedules WHERE owner_id=:ownerId AND preset_id=:presetId AND deleted_at IS NULL LIMIT 1")
  suspend fun getForPreset(ownerId: String, presetId: String): RecurringScheduleEntity?

  @Query("SELECT * FROM recurring_schedules WHERE owner_id=:ownerId AND id=:id")
  suspend fun getById(ownerId: String, id: String): RecurringScheduleEntity?

  @Upsert suspend fun upsert(entity: RecurringScheduleEntity)
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(entity: RecurringScheduleEntity)
  @Update suspend fun replaceFromSync(entity: RecurringScheduleEntity)

  @Query("UPDATE recurring_schedules SET paused=:paused, blocked_reason=:reason, updated_at=:updatedAt, sync_version=:syncVersion WHERE owner_id=:ownerId AND id=:id")
  suspend fun setStatus(ownerId: String, id: String, paused: Boolean, reason: String?, updatedAt: String, syncVersion: String)
}

@Dao
interface RecurringOccurrenceDao {
  @Query("SELECT * FROM recurring_occurrences WHERE owner_id=:ownerId AND schedule_id=:scheduleId AND state='SNOOZED' AND deleted_at IS NULL")
  suspend fun snoozedForSchedule(ownerId: String, scheduleId: String): List<RecurringOccurrenceEntity>
  @Query("SELECT * FROM recurring_occurrences WHERE owner_id=:ownerId AND schedule_id=:scheduleId AND occurrence_key=:key AND deleted_at IS NULL LIMIT 1")
  suspend fun get(ownerId: String, scheduleId: String, key: String): RecurringOccurrenceEntity?

  @Query("SELECT * FROM recurring_occurrences WHERE owner_id=:ownerId AND id=:id")
  suspend fun getById(ownerId: String, id: String): RecurringOccurrenceEntity?

  @Query("SELECT o.* FROM recurring_occurrences o JOIN recurring_schedules s ON s.id=o.schedule_id AND s.owner_id=o.owner_id WHERE o.owner_id=:ownerId AND o.state='SNOOZED' AND o.deleted_at IS NULL AND s.deleted_at IS NULL AND s.paused=0 ORDER BY o.effective_at LIMIT 1")
  suspend fun nextSnoozed(ownerId: String): RecurringOccurrenceEntity?

  @Upsert suspend fun upsert(entity: RecurringOccurrenceEntity)
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(entity: RecurringOccurrenceEntity)
  @Update suspend fun replaceFromSync(entity: RecurringOccurrenceEntity)

  @Query("UPDATE recurring_occurrences SET state=:state, effective_at=:effectiveAt, transaction_id=:transactionId, updated_at=:updatedAt, sync_version=:syncVersion WHERE owner_id=:ownerId AND id=:id")
  suspend fun updateState(ownerId: String, id: String, state: com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceState, effectiveAt: Long, transactionId: String?, updatedAt: String, syncVersion: String)
}
