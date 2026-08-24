package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "presets",
  foreignKeys =
    [
      ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["id"],
        childColumns = ["default_currency_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
      ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["default_category_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
      ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["default_account_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices =
    [
      Index("owner_id"),
      Index("default_currency_id"),
      Index("default_category_id"),
      Index("default_account_id"),
    ],
)
data class PresetEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  val name: String,
  val type: TransactionType,
  @ColumnInfo(name = "default_amount") val defaultAmount: Long?,
  @ColumnInfo(name = "default_currency_id") val defaultCurrencyId: String?,
  @ColumnInfo(name = "default_category_id") val defaultCategoryId: String?,
  @ColumnInfo(name = "default_account_id") val defaultAccountId: String?,
  val archived: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at", defaultValue = "''") val updatedAt: String,
)
