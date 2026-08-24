package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Per-owner pull watermark: keyset cursor over (updated_at, id). */
@Entity(tableName = "sync_meta", primaryKeys = ["owner_id", "table_name"])
data class SyncMetaEntity(
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "table_name") val tableName: String,
  @ColumnInfo(name = "last_sync_at") val lastSyncAt: String,
  @ColumnInfo(name = "last_sync_id") val lastSyncId: String? = null,
)
