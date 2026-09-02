package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "currencies",
  indices = [Index("owner_id"), Index(value = ["owner_id", "code"], unique = true)],
)
data class CurrencyEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  val code: String,
  val symbol: String,
  val name: String,
  @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at", defaultValue = "''") val updatedAt: String,
  @ColumnInfo(name = "sync_version", defaultValue = "''") val syncVersion: String = updatedAt,
  @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)
