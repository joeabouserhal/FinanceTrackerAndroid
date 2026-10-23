package com.joeabouserhal.financetracker.data.recurring

import com.joeabouserhal.financetracker.data.local.withMutation
import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.validateReferences
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
      val candidate = Instant.ofEpochMilli(effectiveAt(schedule, next, zone))
      if (candidate.isAfter(now)) candidate else RecurrenceCalculator.next(schedule, today.plusDays(1))?.let { Instant.ofEpochMilli(effectiveAt(schedule, it, zone)) }
    }.minOrNull()

  suspend fun saveSchedule(ownerId: String, presetId: String, draft: RecurrenceDraft): RecurringScheduleEntity {
    validate(draft)
    val now = Instant.now().toString()
    return db.withMutation {
    val preset = db.presetDao().getById(ownerId, presetId)
    require(preset != null && preset.deletedAt == null && !preset.archived) { "Preset is unavailable" }
    require(preset.defaultAmount != null && preset.defaultAmount > 0 && preset.defaultCurrencyId != null && preset.defaultAccountId != null && preset.defaultCategoryId != null) { "A recurring preset needs an amount, currency, account, and category" }
    db.validateReferences(ownerId, preset.defaultCurrencyId, preset.defaultAccountId, preset.defaultCategoryId, preset.type)
    val existing = db.recurringScheduleDao().getForPreset(ownerId, presetId)
    val row = RecurringScheduleEntity(existing?.id ?: UUID.randomUUID().toString(), ownerId, presetId, draft.frequency, draft.interval,
      draft.weekDays.sorted().joinToString(",").ifBlank { null }, draft.monthlyRule, draft.dayOfMonth, draft.nthWeekday, draft.nthOrdinal,
      draft.startDate, draft.endDate, draft.timeMinutes, false, null, existing?.createdAt ?: now, now, existing?.syncVersion ?: now, null)
    db.withMutation { db.recurringScheduleDao().upsert(row); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", if (existing == null) OutboxAction.INSERT else OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, row)) }
    // Editing a schedule changes future occurrences, not historical entries.
    if (existing != null) db.recurringScheduleDao().saveProgress(com.joeabouserhal.financetracker.data.local.entities.RecurringProgressEntity(ownerId, row.id, LocalDate.now().minusDays(1).toString()))
    row
    }
  }

  suspend fun setPaused(ownerId: String, scheduleId: String, paused: Boolean, reason: String? = null) {
    db.withMutation {
    val current = db.recurringScheduleDao().getById(ownerId, scheduleId)?.takeIf { it.deletedAt == null } ?: return@withMutation
    val now = Instant.now().toString()
    val updated = current.copy(paused = paused, blockedReason = reason, updatedAt = now)
    db.withMutation { db.recurringScheduleDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, updated)) }
    }
  }

  suspend fun skipNext(ownerId: String, scheduleId: String, today: LocalDate = LocalDate.now()): Boolean = db.withMutation {
    val schedule = db.recurringScheduleDao().getById(ownerId, scheduleId)?.takeIf { it.deletedAt == null && !it.paused } ?: return@withMutation false
    var date = RecurrenceCalculator.next(schedule, today) ?: return@withMutation false
    while (db.recurringOccurrenceDao().get(ownerId, schedule.id, date.toString()) != null) {
      date = RecurrenceCalculator.next(schedule, date.plusDays(1)) ?: return@withMutation false
    }
    val now = Instant.now().toString(); val id = occurrenceId(ownerId, schedule.id, date.toString())
    val occurrence = RecurringOccurrenceEntity(id, ownerId, schedule.id, date.toString(), RecurringOccurrenceState.SKIPPED, effectiveAt(schedule, date), null, now, now)
    db.withMutation { db.recurringOccurrenceDao().upsert(occurrence); OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, occurrence)) }
    true
  }

  suspend fun verify(ownerId: String, occurrenceId: String) = updateOccurrence(ownerId, occurrenceId, RecurringOccurrenceState.VERIFIED)

  suspend fun snooze(ownerId: String, occurrenceId: String, until: Instant) {
    db.withMutation {
      val occurrence = db.recurringOccurrenceDao().getById(ownerId, occurrenceId)?.takeIf { it.deletedAt == null && it.state == RecurringOccurrenceState.POSTED } ?: return@withMutation
      require(until.isAfter(Instant.now())) { "Choose a future snooze time" }
      occurrence.transactionId?.let { transactions.remove(ownerId, it) }
      val now = Instant.now().toString()
      val changed = occurrence.copy(state = RecurringOccurrenceState.SNOOZED, effectiveAt = until.toEpochMilli(), transactionId = null, updatedAt = now, generation = occurrence.generation + 1)
      db.recurringOccurrenceDao().upsert(changed)
      OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, changed))
    }
  }

  /** Called by the durable worker. Existing occurrence rows make catch-up and restarts idempotent. */
  suspend fun postDue(ownerId: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<RecurringDue> {
    val today = now.atZone(zone).toLocalDate(); val due = mutableListOf<RecurringDue>()
    db.withTransaction {
      db.recurringScheduleDao().getActiveRows(ownerId).filter { !it.paused }.forEach { schedule ->
        val invalid = runCatching { validate(RecurrenceDraft(schedule.frequency, schedule.interval,
          schedule.weekDays.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet(), schedule.monthlyRule,
          schedule.dayOfMonth, schedule.nthWeekday, schedule.nthOrdinal, schedule.startDate, schedule.endDate, schedule.timeMinutes)) }.isFailure
        if (invalid) {
          setBlockedInTransaction(ownerId, schedule, "Schedule needs a valid date, time, and repeat rule")
          return@forEach
        }
        val preset = db.presetDao().getById(ownerId, schedule.presetId)
        if (preset == null || preset.deletedAt != null || preset.archived || preset.defaultAmount == null || preset.defaultAmount <= 0 || preset.defaultCurrencyId == null || preset.defaultAccountId == null || preset.defaultCategoryId == null) {
          setBlockedInTransaction(ownerId, schedule, "Preset needs valid amount, currency, account, and category")
          return@forEach
        }
        val currency = db.currencyDao().getById(ownerId, preset.defaultCurrencyId)
        val account = db.accountDao().getById(ownerId, preset.defaultAccountId)
        val category = db.categoryDao().getById(ownerId, preset.defaultCategoryId)
        if (currency == null || currency.deletedAt != null || account == null || account.deletedAt != null || account.archived || account.currencyId != currency.id || category == null || category.deletedAt != null || category.type != preset.type) {
          setBlockedInTransaction(ownerId, schedule, "Choose an available currency, account, and category in the preset")
          return@forEach
        }
        val processed = db.recurringScheduleDao().processedThrough(ownerId, schedule.id)?.let(LocalDate::parse)
        val newDates = RecurrenceCalculator.occurrences(schedule, today, processed?.plusDays(1) ?: LocalDate.parse(schedule.startDate))
        val snoozedDates = db.recurringOccurrenceDao().snoozedForSchedule(ownerId, schedule.id).map { LocalDate.parse(it.occurrenceKey) }
        (newDates + snoozedDates.asSequence()).distinct().forEach { date ->
          val id = occurrenceId(ownerId, schedule.id, date.toString())
          val existing = db.recurringOccurrenceDao().get(ownerId, schedule.id, date.toString())
          if (existing?.state == RecurringOccurrenceState.POSTED || existing?.state == RecurringOccurrenceState.VERIFIED || existing?.state == RecurringOccurrenceState.SKIPPED) return@forEach
          val effective = existing?.effectiveAt ?: effectiveAt(schedule, date, zone)
          if (effective > now.toEpochMilli()) return@forEach
          // Each occurrence is its own remote logical action. A stale occurrence
          // must not discard unrelated missed entries in the same catch-up run.
          db.withMutation {
          val transaction = transactions.add(ownerId, preset.type, preset.defaultAmount, preset.defaultCurrencyId, preset.defaultCategoryId, preset.defaultAccountId,
            Instant.ofEpochMilli(effective).atZone(zone).toLocalDate().toString(), preset.name, null, preset.id, recurrenceOccurrenceId = id, recurrenceGeneration = existing?.generation ?: 0)
          val stamp = Instant.now().toString()
          val occurrence = RecurringOccurrenceEntity(id, ownerId, schedule.id, date.toString(), RecurringOccurrenceState.POSTED, effective, transaction.id, existing?.createdAt ?: stamp, stamp, existing?.syncVersion ?: stamp, null, existing?.generation ?: 0)
          db.recurringOccurrenceDao().upsert(occurrence)
          OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", if (existing == null) OutboxAction.INSERT else OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, occurrence))
          due += RecurringDue(id, preset.name, preset.defaultAmount, db.currencyDao().getById(ownerId, preset.defaultCurrencyId)?.code ?: "")
          }
        }
        val through = if (effectiveAt(schedule, today, zone) <= now.toEpochMilli()) today else today.minusDays(1)
        db.recurringScheduleDao().saveProgress(com.joeabouserhal.financetracker.data.local.entities.RecurringProgressEntity(ownerId, schedule.id, through.toString()))
      }
    }
    return due
  }

  private suspend fun updateOccurrence(ownerId: String, id: String, state: RecurringOccurrenceState) {
    db.withMutation {
    val current = db.recurringOccurrenceDao().getById(ownerId, id)?.takeIf { it.deletedAt == null && it.state == RecurringOccurrenceState.POSTED } ?: return@withMutation
    val now = Instant.now().toString(); val updated = current.copy(state = state, updatedAt = now)
    db.withMutation { db.recurringOccurrenceDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_occurrences", OutboxAction.UPDATE, OutboxWriter.recurringOccurrence(ownerId, updated)) }
    }
  }

  private suspend fun setBlockedInTransaction(ownerId: String, schedule: RecurringScheduleEntity, reason: String) {
    if (schedule.blockedReason == reason && schedule.paused) return
    val updated = schedule.copy(paused = true, blockedReason = reason, updatedAt = Instant.now().toString())
    db.recurringScheduleDao().upsert(updated); OutboxWriter.enqueue(db, ownerId, "recurring_schedules", OutboxAction.UPDATE, OutboxWriter.recurringSchedule(ownerId, updated))
  }

  private fun validate(draft: RecurrenceDraft) {
    require(draft.interval in 1..365) { "Repeat interval must be between 1 and 365" }
    require(draft.timeMinutes in 0 until 24 * 60) { "Choose a valid time" }
    LocalDate.parse(draft.startDate)
    draft.endDate?.let(LocalDate::parse)
    require(draft.weekDays.all { it in 1..7 }) { "Choose valid weekdays" }
    require(draft.endDate == null || draft.endDate >= draft.startDate) { "End date must be after the start date" }
    if (draft.frequency == RecurrenceFrequency.WEEKLY) require(draft.weekDays.isNotEmpty()) { "Choose at least one weekday" }
    if (draft.frequency == RecurrenceFrequency.MONTHLY && draft.monthlyRule == MonthlyRule.DAY_OF_MONTH) require(draft.dayOfMonth in 1..31) { "Choose a monthly day" }
    if (draft.frequency == RecurrenceFrequency.MONTHLY && draft.monthlyRule == MonthlyRule.NTH_WEEKDAY) require(draft.nthWeekday in 1..7 && draft.nthOrdinal in 1..5) { "Choose a valid weekday and occurrence" }
  }

  private fun occurrenceId(ownerId: String, scheduleId: String, key: String): String = UUID.nameUUIDFromBytes("$ownerId:$scheduleId:$key".toByteArray(StandardCharsets.UTF_8)).toString()
  private fun effectiveAt(schedule: RecurringScheduleEntity, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = recurringTime(date, schedule.timeMinutes, zone).toEpochMilli()
}

/** Resolve the selected wall-clock time, not elapsed minutes since a DST-changing midnight. */
internal fun recurringTime(date: LocalDate, minutes: Int, zone: ZoneId): Instant =
  date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant()
