package com.joeabouserhal.financetracker.data.repositories

import com.joeabouserhal.financetracker.data.local.withMutation
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.GoalDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
  private val dao: TransactionDao,
  private val categoryDao: CategoryDao,
  private val categoryRepository: CategoryRepository,
  private val goalDao: GoalDao,
  private val db: AppDatabase,
) {
  fun observeAll(ownerId: String): Flow<List<TransactionEntity>> = dao.observeAll(ownerId)

  fun observeBetween(ownerId: String, dateFrom: String, dateTo: String): Flow<List<TransactionEntity>> =
    dao.observeBetween(ownerId, dateFrom, dateTo)

  fun observeRecent(ownerId: String, limit: Int): Flow<List<TransactionEntity>> = dao.observeRecent(ownerId, limit)

  fun observeForAccount(ownerId: String, accountId: String): Flow<List<TransactionEntity>> = dao.observeForAccount(ownerId, accountId)
  fun observePage(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<TransactionEntity>> = dao.observePage(query)

  suspend fun add(
    ownerId: String,
    type: TransactionType,
    amount: Long,
    currencyId: String,
    categoryId: String?,
    accountId: String?,
    date: String?,
    title: String?,
    notes: String?,
    presetId: String?,
    goalId: String? = null,
    recurrenceOccurrenceId: String? = null,
    recurrenceGeneration: Int = 0,
  ): TransactionEntity = db.withMutation {
    require(type != TransactionType.TRANSFER) { "Use the transfer form to move funds" }
    require(amount > 0) { "Amount must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    // Enforced category: fall back to the seeded "Other" of this type.
    val resolvedCategoryId = categoryId?.takeIf { it.isNotBlank() } ?: resolveOther(ownerId, type)
    val resolvedDate = date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
    validateReferences(ownerId, type, currencyId, resolvedCategoryId, accountId, resolvedDate, presetId, goalId)
    val recurringId = recurrenceOccurrenceId?.let {
      UUID.nameUUIDFromBytes("$ownerId:$it:$recurrenceGeneration".toByteArray(Charsets.UTF_8)).toString()
    }
    if (recurrenceOccurrenceId != null) {
      dao.findRecurring(ownerId, recurrenceOccurrenceId, recurrenceGeneration)?.let { return@withMutation it }
    }

    val now = java.time.Instant.now().toString()
    val entity =
      TransactionEntity(
        id = recurringId ?: UUID.randomUUID().toString(),
        ownerId = ownerId,
        type = type,
        amount = amount,
        currencyId = currencyId,
        categoryId = resolvedCategoryId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        date = resolvedDate,
        title = title?.takeIf { it.isNotBlank() },
        notes = notes?.takeIf { it.isNotBlank() },
        presetId = presetId?.takeIf { it.isNotBlank() },
        goalId = goalId?.takeIf { it.isNotBlank() },
        recurrenceOccurrenceId = recurrenceOccurrenceId?.takeIf { it.isNotBlank() },
        recurrenceGeneration = recurrenceGeneration,
        createdAt = now,
        updatedAt = now,
      )
    db.withMutation {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.INSERT, OutboxWriter.transaction(ownerId, entity))
    }
    entity
  }

  suspend fun update(
    ownerId: String,
    id: String,
    type: TransactionType,
    amount: Long,
    currencyId: String,
    categoryId: String?,
    accountId: String?,
    date: String?,
    title: String?,
    notes: String?,
    presetId: String?,
  ) = db.withMutation {
    require(amount > 0) { "Amount must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    // Manual edits don't clear the "created from preset" lineage: only an
    // explicit presetId wins over the existing one. Goal linkage is never
    // editable from the form, so it always survives — and a goal withdrawal
    // can never be flipped to income.
    val existing = dao.getById(ownerId, id)
    require(existing != null && existing.deletedAt == null) { "This transaction no longer exists" }
    require(existing?.type != TransactionType.TRANSFER && type != TransactionType.TRANSFER) { "Use the transfer form to edit this entry" }
    val effectiveType = if (existing?.goalId != null) TransactionType.GOAL else type
    // GOAL has no "Other" of its own type; goal withdrawals use the Goal category.
    val resolvedCategoryId =
      when {
        existing?.goalId != null -> categoryId?.takeIf { it.isNotBlank() } ?: categoryRepository.ensureGoalCategory(ownerId).id
        else -> categoryId?.takeIf { it.isNotBlank() } ?: resolveOther(ownerId, type)
      }
    db.withMutation {
      validateReferences(ownerId, effectiveType, currencyId, resolvedCategoryId, accountId,
        date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString(), presetId ?: existing.presetId, existing.goalId, existing.accountId)
      dao.update(
        ownerId = ownerId,
        id = id,
        type = effectiveType,
        amount = amount,
        currencyId = currencyId,
        categoryId = resolvedCategoryId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        date = date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString(),
        title = title?.takeIf { it.isNotBlank() },
        notes = notes?.takeIf { it.isNotBlank() },
        presetId = presetId?.takeIf { it.isNotBlank() } ?: existing?.presetId,
        goalId = existing?.goalId,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.UPDATE, OutboxWriter.transaction(ownerId, full))
      }
    }
  }

  /**
   * Deletes one transaction. When it is a GOAL withdrawal, the originating
   * goal is un-completed and every other transaction of that goal (multi-
   * account goals produce several) is deleted too.
   */
  suspend fun remove(ownerId: String, id: String) {
    db.withMutation {
      val existing = dao.getById(ownerId, id)
      if (existing == null || existing.deletedAt != null) return@withMutation
      val goalId = existing?.goalId
      if (existing != null && existing.type == TransactionType.GOAL && goalId != null) {
        val goal = goalDao.getById(ownerId, goalId)
        if (goal != null && goal.completed) {
          goalDao.update(
            ownerId,
            goal.id,
            goal.name,
            goal.targetMinor,
            goal.currencyId,
            goal.accountId,
            completed = false,
            updatedAt = java.time.Instant.now().toString(),
          )
          goalDao.getById(ownerId, goal.id)?.let { full ->
            OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
          }
        }
        dao.getByGoal(ownerId, goalId).forEach { sibling ->
          val deletedAt = java.time.Instant.now().toString()
          if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, sibling.id)
          else dao.delete(ownerId, sibling.id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
          OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.DELETE, OutboxWriter.deletePayload(sibling.id))
        }
      } else {
        val deletedAt = java.time.Instant.now().toString()
        if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, id)
        else dao.delete(ownerId, id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
        OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
      }
    }
  }

  /** One row contains both account legs, committed with its outbox operation. */
  suspend fun saveTransfer(
    ownerId: String, id: String? = null, amount: Long, currencyId: String,
    sourceAccountId: String, destinationAccountId: String, date: String,
    title: String? = null, notes: String? = null,
  ): TransactionEntity = db.withMutation {
    require(amount > 0) { "Amount must be positive" }
    require(sourceAccountId != destinationAccountId) { "Choose two different accounts" }
    require(runCatching { LocalDate.parse(date) }.isSuccess) { "Choose a valid date" }
    val currency = db.currencyDao().getById(ownerId, currencyId)
    require(currency != null && currency.deletedAt == null) { "Currency is unavailable" }
    val existing = id?.let { dao.getById(ownerId, it) }
    require(id == null || (existing != null && existing.deletedAt == null && existing.type == TransactionType.TRANSFER)) {
      "This transfer no longer exists"
    }
    listOf(sourceAccountId, destinationAccountId).forEach { accountId ->
      val account = db.accountDao().getById(ownerId, accountId)
      require(account != null && account.deletedAt == null && account.currencyId == currencyId) {
        "Both accounts must belong to this profile and use the selected currency"
      }
      require(!account.archived || accountId == existing?.accountId || accountId == existing?.destinationAccountId) {
        "Choose an active account"
      }
    }
    val now = java.time.Instant.now().toString()
    val entity = TransactionEntity(
      id = existing?.id ?: UUID.randomUUID().toString(), ownerId = ownerId,
      type = TransactionType.TRANSFER, amount = amount, currencyId = currencyId,
      categoryId = null, accountId = sourceAccountId, destinationAccountId = destinationAccountId,
      date = date, title = title?.trim()?.takeIf { it.isNotBlank() }, notes = notes?.trim()?.takeIf { it.isNotBlank() },
      presetId = null, goalId = null, createdAt = existing?.createdAt ?: now, updatedAt = now,
    )
    dao.upsert(entity)
    OutboxWriter.enqueue(db, ownerId, "transactions", if (existing == null) OutboxAction.INSERT else OutboxAction.UPDATE,
      OutboxWriter.transaction(ownerId, entity))
    entity
  }

  private suspend fun resolveOther(ownerId: String, type: TransactionType): String =
    categoryDao.getDefaultOther(ownerId, type)?.id
      ?: throw IllegalStateException("No default category found for $type")

  private suspend fun validateReferences(
    ownerId: String, type: TransactionType, currencyId: String, categoryId: String,
    accountId: String?, date: String, presetId: String?, goalId: String?, existingAccountId: String? = null,
  ) {
    require(runCatching { LocalDate.parse(date) }.isSuccess) { "Choose a valid date" }
    require(db.currencyDao().getById(ownerId, currencyId)?.deletedAt == null && db.currencyDao().getById(ownerId, currencyId) != null) { "Currency is unavailable" }
    val category = categoryDao.getById(ownerId, categoryId)
    val categoryType = if (type == TransactionType.GOAL) TransactionType.EXPENSE else type
    require(category != null && category.deletedAt == null && category.type == categoryType) { "Choose a category for this transaction type" }
    accountId?.let {
      val account = db.accountDao().getById(ownerId, it)
      require(account != null && account.deletedAt == null && account.currencyId == currencyId && (!account.archived || it == existingAccountId)) { "Choose an active account in the selected currency" }
    }
    presetId?.let { require(db.presetDao().getById(ownerId, it) != null) { "Preset is unavailable" } }
    goalId?.let {
      val goal = goalDao.getById(ownerId, it)
      require(goal != null && goal.deletedAt == null && goal.currencyId == currencyId) { "Goal is unavailable for this currency" }
    }
  }
}
