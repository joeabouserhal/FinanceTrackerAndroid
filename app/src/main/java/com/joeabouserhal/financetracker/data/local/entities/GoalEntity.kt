package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monetary goal: target amount in one currency, tracked either against the
 * currency's total balance or a specific account of that currency.
 */
@Entity(
  tableName = "goals",
  foreignKeys =
    [
      ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["id"],
        childColumns = ["currency_id"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices = [Index("owner_id"), Index("currency_id"), Index("account_id")],
)
data class GoalEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  val name: String,
  @ColumnInfo(name = "target_minor") val targetMinor: Long,
  @ColumnInfo(name = "currency_id") val currencyId: String,
  @ColumnInfo(name = "account_id") val accountId: String?,
  @ColumnInfo(name = "completed", defaultValue = "0") val completed: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at", defaultValue = "''") val updatedAt: String,
  @ColumnInfo(name = "sync_version", defaultValue = "''") val syncVersion: String = updatedAt,
  @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)
