package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per owner: the user's display name and account creation date. */
@Entity(tableName = "profiles")
data class ProfileEntity(
  @PrimaryKey @ColumnInfo(name = "owner_id") val ownerId: String,
  val name: String,
  @ColumnInfo(name = "updated_at") val updatedAt: String,
  @ColumnInfo(name = "created_at", defaultValue = "''") val createdAt: String,
  @ColumnInfo(name = "sync_version", defaultValue = "''") val syncVersion: String = updatedAt,
)
