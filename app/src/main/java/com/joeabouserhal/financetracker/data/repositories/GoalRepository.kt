package com.joeabouserhal.financetracker.data.repositories

import androidx.room.withTransaction
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.dao.GoalDao
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** One account deduction produced by completing a goal. */
data class GoalDeduction(
  val accountId: String,
  val amountMinor: Long,
)

/** What the completion modal needs: the goal name and the per-account removals. */
data class GoalCompletionResult(
  val goalName: String,
  val deductions: List<GoalDeduction>,
)

class GoalRepository(
  private val dao: GoalDao,
  private val categoryRepository: CategoryRepository,
  private val transactionRepository: TransactionRepository,
  private val db: AppDatabase,
) {
  fun observeAll(ownerId: String): Flow<List<GoalEntity>> = dao.observeAll(ownerId)

  suspend fun add(
    ownerId: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
  ): GoalEntity {
    require(name.trim().isNotBlank()) { "Goal name is required" }
    require(targetMinor > 0) { "Goal target must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    val now = java.time.Instant.now().toString()
    val entity =
      GoalEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        name = name.trim(),
        targetMinor = targetMinor,
        currencyId = currencyId,
        accountId = accountId?.takeIf { it.isNotBlank() },
        createdAt = now,
        updatedAt = now,
      )
    db.withTransaction {
      dao.upsert(entity)
      OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.INSERT, OutboxWriter.goal(ownerId, entity))
    }
    return entity
  }

  suspend fun update(
    ownerId: String,
    id: String,
    name: String,
    targetMinor: Long,
    currencyId: String,
    accountId: String?,
  ) {
    require(name.trim().isNotBlank()) { "Goal name is required" }
    require(targetMinor > 0) { "Goal target must be positive" }
    require(currencyId.isNotBlank()) { "Currency is required" }
    db.withTransaction {
      dao.update(
        ownerId,
        id,
        name.trim(),
        targetMinor,
        currencyId,
        accountId?.takeIf { it.isNotBlank() },
        completed = false,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
    }
  }

  /**
   * Complete a goal: removes [allocations] (accountId → minor amount) from
   * the given accounts as custom GOAL transactions, then marks the goal
   * completed. Only accounts with a positive allocation get a transaction.
   */
  suspend fun complete(
    ownerId: String,
    id: String,
    allocations: Map<String, Long>,
  ): GoalCompletionResult? {
    val deductions =
      allocations
        .filter { (_, amount) -> amount > 0 }
        .toList()
        .sortedBy { it.first }
    require(deductions.isNotEmpty()) { "At least one account must be debited" }

    return db.withTransaction {
      // Re-check inside the transaction: Room serializes write transactions,
      // so a double-tap or racing completion can't double-debit the goal.
      val existing = dao.getById(ownerId, id) ?: return@withTransaction null
      if (existing.completed) return@withTransaction null

      require(deductions.sumOf { it.second } == existing.targetMinor) {
        "Account splits must add up to the goal amount"
      }
      // Every debited account must belong to the goal's currency — otherwise
      // the withdrawal would move money out of a foreign currency.
      val accountDao = db.accountDao()
      deductions.forEach { (accountId, _) ->
        val account = accountDao.getById(ownerId, accountId)
        require(account != null && account.currencyId == existing.currencyId) {
          "Every split must go to an account of the goal's currency"
        }
      }

      // Goal transactions use the dedicated "Goal" category (created on first
      // use) rather than the generic expense "Other".
      val categoryId = categoryRepository.ensureGoalCategory(ownerId).id

      deductions.forEach { (accountId, amount) ->
        transactionRepository.add(
          ownerId = ownerId,
          type = TransactionType.GOAL,
          amount = amount,
          currencyId = existing.currencyId,
          categoryId = categoryId,
          accountId = accountId,
          date = null,
          title = existing.name,
          notes = null,
          presetId = null,
          goalId = existing.id,
        )
      }
      dao.update(
        ownerId,
        id,
        existing.name,
        existing.targetMinor,
        existing.currencyId,
        existing.accountId,
        completed = true,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
      GoalCompletionResult(existing.name, deductions.map { GoalDeduction(it.first, it.second) })
    }
  }

  /** Undo a completed goal — fully reverses it: removes its GOAL withdrawals
   * and moves it back to the active list. */
  suspend fun markActive(ownerId: String, id: String) {
    db.withTransaction {
      val existing = dao.getById(ownerId, id) ?: return@withTransaction
      if (!existing.completed) return@withTransaction

      // Remove every withdrawal this goal produced (multi-account goals
      // create several), then un-complete.
      db.transactionDao().getByGoal(ownerId, id).forEach { tx ->
        val deletedAt = java.time.Instant.now().toString()
        if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) db.transactionDao().delete(ownerId, tx.id)
        else db.transactionDao().delete(ownerId, tx.id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
        OutboxWriter.enqueue(db, ownerId, "transactions", OutboxAction.DELETE, OutboxWriter.deletePayload(tx.id))
      }
      dao.update(
        ownerId,
        id,
        existing.name,
        existing.targetMinor,
        existing.currencyId,
        existing.accountId,
        completed = false,
        updatedAt = java.time.Instant.now().toString(),
      )
      dao.getById(ownerId, id)?.let { full ->
        OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.UPDATE, OutboxWriter.goal(ownerId, full))
      }
    }
  }

  suspend fun delete(ownerId: String, id: String) {
    db.withTransaction {
      val deletedAt = java.time.Instant.now().toString()
      if (ownerId == com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID) dao.delete(ownerId, id)
      else dao.delete(ownerId, id, deletedAt, com.joeabouserhal.financetracker.data.sync.SyncVersion.next())
      OutboxWriter.enqueue(db, ownerId, "goals", OutboxAction.DELETE, OutboxWriter.deletePayload(id))
    }
  }
}
