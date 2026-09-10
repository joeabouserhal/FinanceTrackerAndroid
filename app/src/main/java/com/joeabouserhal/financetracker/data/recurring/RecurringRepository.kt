package com.joeabouserhal.financetracker.data.recurring

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceEntity
import com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceState
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency
import com.joeabouserhal.financetracker.data.repositories.TransactionRepository
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class RecurrenceDraft(
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  val weekDays: Set<Int> = emptySet(),
  val monthlyRule: MonthlyRule = MonthlyRule.DAY_OF_MONTH,
  val dayOfMonth: Int? = null,
  val nthWeekday: Int? = null,
  val nthOrdinal: Int? = null,
  val startDate: String,
  val endDate: String? = null,
  val timeMinutes: Int = 9 * 60,
)

data class RecurringDue(val occurrenceId: String, val presetName: String, val amount: Long, val currencyCode: String)

class RecurringRepository(
  private val db: AppDatabase,
  private val transactions: TransactionRepository,
) {
  fun observeSchedules(ownerId: String): Flow<List<RecurringScheduleEntity>> = db.recurringScheduleDao().observeAll(ownerId)
  suspend fun scheduleForPreset(ownerId: String, presetId: String) = db.recurringScheduleDao().getForPreset(ownerId, presetId)
  suspend fun nextSnooze(ownerId: String): Instant? = db.recurringOccurrenceDao().nextSnoozed(ownerId)?.let { Instant.ofEpochMilli(it.effectiveAt) }
  suspend fun nextDue(ownerId: String, today: LocalDate, now: Instant, zone: ZoneId): Instant? =
    db.recurringScheduleDao().getActiveRows(ownerId).filter { !it.paused }.mapNotNull { schedule ->
      val next = RecurrenceCalculator.next(schedule, today) ?: return@mapNotNull null
      val candidate = Instant.ofEpochMilli(effectiveAt(schedule, next))
      if (candidate.isAfter(now)) candidate else RecurrenceCalculator.next(schedule, today.plusDays(1))?.let { Instant.ofEpochMilli(effectiveAt(schedule, it)) }
    }.minOrNull()

  suspend fun saveSchedule(ownerId: String, presetId: String, draft: RecurrenceDraft): RecurringScheduleEntity {
    validate(draft)
    val now = Instant.now().toString()
    val existing = db.recurringScheduleDao().getForPreset(ownerId, presetId)
    val row = RecurringScheduleEntity(existing?.id ?: UUID.randomUUID().toString(), ownerId, presetId, draft.frequency, draft.interval,
      draft.weekDays.sorted().joinToString(",").ifBlank { null }, draft.monthlyRule, draft.dayOfMonth, draft.nthWeekday, draft.nthOrdinal,
      draft.startDate, draft.endDate, draft.timeMinutes, false, null, existing?.createdAt ?: now, now, existing?.syncVersion ?: now, null)
    db.withTransaction { db.recurringScheduleDao().upsert(row); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", if (existing == null) OutboxAction.INSERT else OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, row)) }
    return row
  }

  suspend fun setPaused(ownerId: String, scheduleId: String, paused: Boolean, reason: String? = null) {
    val current = db.recurringScheduleDao().getById(ownerId, scheduleId) ?: return
    val now = Instant.now().toString()
    val updated = current.copy(paused = paused, blockedReason = reason, updatedAt = now)
    db.withTransaction { db.recurringScheduleDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, updated)) }
  }

  suspend fun skipNext(ownerId: String, scheduleId: String, today: LocalDate = LocalDate.now()): Boolean {
    val schedule = db.recurringScheduleDao().getById(ownerId, scheduleId) ?: return false
    val date = RecurrenceCalculator.next(schedule, today) ?: return false
    val now = Instant.now().toString(); val id = occurrenceId(ownerId, schedule.id, date.toString())
    val occurrence = RecurringOccurrenceEntity(id, ownerId, schedule.id, date.toString(), RecurringOccurrenceState.SKIPPED, effectiveAt(schedule, date), null, now, now)
    db.withTransaction { db.recurringOccurrenceDao().upsert(occurrence); OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, occurrence)) }
    return true
  }

  suspend fun verify(ownerId: String, occurrenceId: String) = updateOccurrence(ownerId, occurrenceId, RecurringOccurrenceState.VERIFIED)

  suspend fun snooze(ownerId: String, occurrenceId: String, until: Instant) {
    val occurrence = db.recurringOccurrenceDao().getById(ownerId, occurrenceId) ?: return
    db.withTransaction {
      occurrence.transactionId?.let { transactions.remove(ownerId, it) }
      val now = Instant.now().toString()
      val changed = occurrence.copy(state = RecurringOccurrenceState.SNOOZED, effectiveAt = until.toEpochMilli(), transactionId = null, updatedAt = now)
      db.recurringOccurrenceDao().upsert(changed)
      OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, changed))
    }
  }

  /** Called by the durable worker. Existing occurrence rows make catch-up and restarts idempotent. */
  suspend fun postDue(ownerId: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<RecurringDue> {
    val today = now.atZone(zone).toLocalDate(); val due = mutableListOf<RecurringDue>()
    db.withTransaction {
      db.recurringScheduleDao().getActiveRows(ownerId).filter { !it.paused }.forEach { schedule ->
        val preset = db.presetDao().getById(ownerId, schedule.presetId)
        if (preset == null || preset.archived || preset.defaultAmount == null || preset.defaultCurrencyId == null || preset.defaultAccountId == null || preset.defaultCategoryId == null) {
          setBlockedInTransaction(ownerId, schedule, "Preset needs valid amount, currency, account, and category")
          return@forEach
        }
        RecurrenceCalculator.occurrences(schedule, today).forEach { date ->
          val id = occurrenceId(ownerId, schedule.id, date.toString())
          val existing = db.recurringOccurrenceDao().get(ownerId, schedule.id, date.toString())
          if (existing?.state == RecurringOccurrenceState.POSTED || existing?.state == RecurringOccurrenceState.VERIFIED || existing?.state == RecurringOccurrenceState.SKIPPED) return@forEach
          val effective = existing?.effectiveAt ?: effectiveAt(schedule, date)
          if (effective > now.toEpochMilli()) return@forEach
          val transaction = transactions.add(ownerId, preset.type, preset.defaultAmount, preset.defaultCurrencyId, preset.defaultCategoryId, preset.defaultAccountId,
            Instant.ofEpochMilli(effective).atZone(zone).toLocalDate().toString(), preset.name, null, preset.id, recurrenceOccurrenceId = id)
          val stamp = Instant.now().toString()
          val occurrence = RecurringOccurrenceEntity(id, ownerId, schedule.id, date.toString(), RecurringOccurrenceState.POSTED, effective, transaction.id, existing?.createdAt ?: stamp, stamp, existing?.syncVersion ?: stamp, null)
          db.recurringOccurrenceDao().upsert(occurrence)
          OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", if (existing == null) OutboxAction.INSERT else OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, occurrence))
          due += RecurringDue(id, preset.name, preset.defaultAmount, db.currencyDao().getById(ownerId, preset.defaultCurrencyId)?.code ?: "")
        }
      }
    }
    return due
  }

  private suspend fun updateOccurrence(ownerId: String, id: String, state: RecurringOccurrenceState) {
    val current = db.recurringOccurrenceDao().getById(ownerId, id) ?: return
    val now = Instant.now().toString(); val updated = current.copy(state = state, updatedAt = now)
    db.withTransaction { db.recurringOccurrenceDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, updated)) }
  }

  private suspend fun setBlockedInTransaction(ownerId: String, schedule: RecurringScheduleEntity, reason: String) {
    if (schedule.blockedReason == reason && schedule.paused) return
    val updated = schedule.copy(paused = true, blockedReason = reason, updatedAt = Instant.now().toString())
    db.recurringScheduleDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, updated))
  }

  private fun validate(draft: RecurrenceDraft) {
    require(draft.interval in 1..365) { "Repeat interval must be between 1 and 365" }
    require(draft.timeMinutes in 0 until 24 * 60) { "Choose a valid time" }
    require(draft.endDate == null || draft.endDate >= draft.startDate) { "End date must be after the start date" }
    if (draft.frequency == RecurrenceFrequency.WEEKLY) require(draft.weekDays.isNotEmpty()) { "Choose at least one weekday" }
    if (draft.frequency == RecurrenceFrequency.MONTHLY && draft.monthlyRule == MonthlyRule.DAY_OF_MONTH) require(draft.dayOfMonth in 1..31) { "Choose a monthly day" }
  }

  private fun occurrenceId(ownerId: String, scheduleId: String, key: String): String = UUID.nameUUIDFromBytes("$ownerId:$scheduleId:$key".toByteArray(StandardCharsets.UTF_8)).toString()
  private fun effectiveAt(schedule: RecurringScheduleEntity, date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(schedule.timeMinutes.toLong()).toInstant().toEpochMilli()
}
