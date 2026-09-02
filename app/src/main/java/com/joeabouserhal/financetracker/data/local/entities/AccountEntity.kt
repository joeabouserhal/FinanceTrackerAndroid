package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An account belongs to exactly one currency (user requirement: multiple
 * accounts per currency, shown individually).
 */
@Entity(
  tableName = "accounts",
  foreignKeys =
    [
      ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["id"],
        childColumns = ["currency_id"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("owner_id"), Index("currency_id")],
)
data class AccountEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "currency_id") val currencyId: String,
  val name: String,
  val archived: Boolean = false,
  @ColumnInfo(name = "is_default", defaultValue = "0") val isDefault: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at", defaultValue = "''") val updatedAt: String,
  @ColumnInfo(name = "sync_version", defaultValue = "''") val syncVersion: String = updatedAt,
  @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)
