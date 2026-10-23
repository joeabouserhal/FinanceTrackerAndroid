package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Device-local catch-up cursor, never synced or backed up. */
@Entity(tableName = "recurring_progress", primaryKeys = ["owner_id", "schedule_id"])
data class RecurringProgressEntity(
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  @ColumnInfo(name = "through_date") val throughDate: String,
)
