package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "transactions",
  foreignKeys =
    [
      ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["id"],
        childColumns = ["currency_id"],
        onDelete = ForeignKey.RESTRICT,
      ),
      ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["category_id"],
        onDelete = ForeignKey.RESTRICT,
      ),
      ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
      ForeignKey(
        entity = PresetEntity::class,
        parentColumns = ["id"],
        childColumns = ["preset_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
      ForeignKey(
        entity = GoalEntity::class,
        parentColumns = ["id"],
        childColumns = ["goal_id"],
        onDelete = ForeignKey.SET_NULL,
      ),
    ],
  indices =
    [
      Index("owner_id"),
      Index("currency_id"),
      Index("category_id"),
      Index("account_id"),
      Index("preset_id"),
      Index("goal_id"),
      Index(value = ["owner_id", "date"]),
    ],
)
data class TransactionEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  val type: TransactionType,
  /** Amount in minor units (cents), always positive. */
  val amount: Long,
  @ColumnInfo(name = "currency_id") val currencyId: String,
  @ColumnInfo(name = "category_id") val categoryId: String,
  @ColumnInfo(name = "account_id") val accountId: String?,
  /** ISO-8601 local date, e.g. 2026-08-23. */
  val date: String,
  val title: String?,
  val notes: String?,
  @ColumnInfo(name = "preset_id") val presetId: String?,
  /** Goal that produced this withdrawal; set for GOAL-type transactions. */
  @ColumnInfo(name = "goal_id") val goalId: String?,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at") val updatedAt: String,
)
