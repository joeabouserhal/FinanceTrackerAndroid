package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_health")
data class SyncHealthEntity(
  @PrimaryKey @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "last_success_at") val lastSuccessAt: String? = null,
  @ColumnInfo(name = "last_error") val lastError: String? = null,
  @ColumnInfo(name = "error_kind") val errorKind: String? = null,
)
