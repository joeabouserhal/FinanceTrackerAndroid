package com.joeabouserhal.financetracker.data.local.seed

import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.util.UUID

/**
 * First-launch defaults for the offline guest partition. Signed-in users get
 * their defaults from the Supabase pull instead, so this only ever runs for
 * [ownerId] = guest.
 */
object GuestSeeder {
  private fun now(): String = java.time.Instant.now().toString()

  suspend fun seedGuestDefaults(db: AppDatabase, ownerId: String) {
    if (db.profileDao().get(ownerId) == null) {
      val n = now()
      db.profileDao().upsert(ProfileEntity(ownerId = ownerId, name = "Guest", updatedAt = n, createdAt = n))
    }
    if (db.currencyDao().getAll(ownerId).isNotEmpty()) return

    val currencyNow = now()
    val currencyId = UUID.randomUUID().toString()
    db.currencyDao().upsert(
      CurrencyEntity(
        id = currencyId,
        ownerId = ownerId,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        isDefault = true,
        createdAt = currencyNow,
        updatedAt = currencyNow,
      )
    )

    val accountNow = now()
    db.accountDao().upsert(
      AccountEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        currencyId = currencyId,
        name = "Cash",
        archived = false,
        isDefault = true,
        createdAt = accountNow,
        updatedAt = accountNow,
      )
    )

    val expenseDefaults =
      listOf(
        "Groceries" to "#4C9A63",
        "Rent" to "#E8432E",
        "Utilities" to "#F4C430",
        "Transport" to "#77746C",
        "Dining Out" to "#E8432E",
        "Entertainment" to "#4C9A63",
        "Health" to "#E8432E",
        "Shopping" to "#F4C430",
        "Other" to "#77746C",
      )
    val incomeDefaults =
      listOf(
        "Salary" to "#4C9A63",
        "Freelance" to "#4C9A63",
        "Other" to "#77746C",
      )

    val categories =
      expenseDefaults.map { (name, color) ->
        val n = now()
        CategoryEntity(
          id = UUID.randomUUID().toString(),
          ownerId = ownerId,
          name = name,
          type = TransactionType.EXPENSE,
          color = color,
          isDefault = true,
          createdAt = n,
          updatedAt = n,
        )
      } +
        incomeDefaults.map { (name, color) ->
          val n = now()
          CategoryEntity(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name,
            type = TransactionType.INCOME,
            color = color,
            isDefault = true,
            createdAt = n,
            updatedAt = n,
          )
        }

    db.categoryDao().upsertAll(categories)
  }
}
