package com.joeabouserhal.financetracker.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class MonthlyRule { DAY_OF_MONTH, LAST_DAY, LAST_BUSINESS_DAY, NTH_WEEKDAY }
enum class RecurringOccurrenceState { POSTED, VERIFIED, SNOOZED, SKIPPED }

/** One optional, synced schedule owned by a preset. Times are local device minutes after midnight. */
@Entity(
  tableName = "recurring_schedules",
  foreignKeys = [ForeignKey(entity = PresetEntity::class, parentColumns = ["id"], childColumns = ["preset_id"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("owner_id"), Index(value = ["owner_id", "preset_id"], unique = true), Index("preset_id")],
)
data class RecurringScheduleEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "preset_id") val presetId: String,
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  /** Comma-separated ISO day values 1 (Monday) through 7 (Sunday). */
  @ColumnInfo(name = "week_days") val weekDays: String? = null,
  @ColumnInfo(name = "monthly_rule") val monthlyRule: MonthlyRule = MonthlyRule.DAY_OF_MONTH,
  @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
  @ColumnInfo(name = "nth_weekday") val nthWeekday: Int? = null,
  @ColumnInfo(name = "nth_ordinal") val nthOrdinal: Int? = null,
  @ColumnInfo(name = "start_date") val startDate: String,
  @ColumnInfo(name = "end_date") val endDate: String? = null,
  @ColumnInfo(name = "time_minutes") val timeMinutes: Int = 9 * 60,
  val paused: Boolean = false,
  @ColumnInfo(name = "blocked_reason") val blockedReason: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at") val updatedAt: String,
  @ColumnInfo(name = "sync_version") val syncVersion: String = updatedAt,
  @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

/** Durable record for one calendar occurrence; prevents duplicate posts and carries snoozes across devices. */
@Entity(
  tableName = "recurring_occurrences",
  foreignKeys = [ForeignKey(entity = RecurringScheduleEntity::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("owner_id"), Index("schedule_id"), Index(value = ["owner_id", "schedule_id", "occurrence_key"], unique = true)],
)
data class RecurringOccurrenceEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "owner_id") val ownerId: String,
  @ColumnInfo(name = "schedule_id") val scheduleId: String,
  @ColumnInfo(name = "occurrence_key") val occurrenceKey: String,
  val state: RecurringOccurrenceState,
  @ColumnInfo(name = "effective_at") val effectiveAt: Long,
  @ColumnInfo(name = "transaction_id") val transactionId: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: String,
  @ColumnInfo(name = "updated_at") val updatedAt: String,
  @ColumnInfo(name = "sync_version") val syncVersion: String = updatedAt,
  @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
  @ColumnInfo(defaultValue = "0") val generation: Int = 0,
)
