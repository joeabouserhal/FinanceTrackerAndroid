package com.joeabouserhal.financetracker.data.repositories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.dao.AccountDao
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.CurrencyDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var currencyRepo: CurrencyRepository
  private lateinit var accountRepo: AccountRepository
  private lateinit var categoryRepo: CategoryRepository
  private lateinit var transactionRepo: TransactionRepository
  private lateinit var dashboardRepo: DashboardRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val currencyDao: CurrencyDao = db.currencyDao()
    val accountDao: AccountDao = db.accountDao()
    val categoryDao: CategoryDao = db.categoryDao()
    val transactionDao: TransactionDao = db.transactionDao()

    currencyRepo = CurrencyRepository(currencyDao, db)
    accountRepo = AccountRepository(accountDao, transactionDao, db)
    categoryRepo = CategoryRepository(categoryDao, db)
    transactionRepo = TransactionRepository(transactionDao, categoryDao, db)
    dashboardRepo = DashboardRepository(currencyDao, accountDao, db.categoryDao(), transactionDao)

    runBlocking { GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID) }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `transaction falls back to Other category when none provided`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val account = accountRepo.observeActive(GUEST_OWNER_ID).first().first()

    val tx =
      transactionRepo.add(
        ownerId = GUEST_OWNER_ID,
        type = TransactionType.EXPENSE,
        amount = 500,
        currencyId = currency.id,
        categoryId = null,
        accountId = account.id,
        date = null,
        title = null,
        notes = null,
        presetId = null,
      )

    val other = db.categoryDao().getDefaultOther(GUEST_OWNER_ID, TransactionType.EXPENSE)!!
    assertEquals(other.id, tx.categoryId)
  }

  @Test
  fun `transaction rejects non-positive amount`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val thrown =
      try {
        transactionRepo.add(
          ownerId = GUEST_OWNER_ID,
          type = TransactionType.EXPENSE,
          amount = 0,
          currencyId = currency.id,
          categoryId = null,
          accountId = null,
          date = null,
          title = null,
          notes = null,
          presetId = null,
        )
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `deleting a category reassigns its transactions to Other`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val account = accountRepo.observeActive(GUEST_OWNER_ID).first().first()
    val custom = categoryRepo.add(GUEST_OWNER_ID, "Coffee", TransactionType.EXPENSE, "#111111")

    transactionRepo.add(
      ownerId = GUEST_OWNER_ID,
      type = TransactionType.EXPENSE,
      amount = 400,
      currencyId = currency.id,
      categoryId = custom.id,
      accountId = account.id,
      date = null,
      title = null,
      notes = null,
      presetId = null,
    )

    categoryRepo.delete(GUEST_OWNER_ID, custom.id)

    val tx = db.transactionDao().getAll(GUEST_OWNER_ID).first()
    val other = db.categoryDao().getDefaultOther(GUEST_OWNER_ID, TransactionType.EXPENSE)!!
    assertEquals(other.id, tx.categoryId)
  }

  @Test
  fun `deleting Other category is blocked`() = runTest {
    val other = db.categoryDao().getDefaultOther(GUEST_OWNER_ID, TransactionType.EXPENSE)!!
    val thrown =
      try {
        categoryRepo.delete(GUEST_OWNER_ID, other.id)
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `deleting the last currency is blocked`() = runTest {
    val only = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val thrown =
      try {
        currencyRepo.delete(GUEST_OWNER_ID, only.id)
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `dashboard derives per-account and per-currency balances`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val cash = accountRepo.observeActive(GUEST_OWNER_ID).first().first()
    val card = accountRepo.add(GUEST_OWNER_ID, currency.id, "Card")
    val other = db.categoryDao().getDefaultOther(GUEST_OWNER_ID, TransactionType.EXPENSE)!!

    transactionRepo.add(
      ownerId = GUEST_OWNER_ID,
      type = TransactionType.INCOME,
      amount = 1000,
      currencyId = currency.id,
      categoryId = null,
      accountId = cash.id,
      date = "2026-08-10",
      title = null,
      notes = null,
      presetId = null,
    )
    transactionRepo.add(
      ownerId = GUEST_OWNER_ID,
      type = TransactionType.EXPENSE,
      amount = 200,
      currencyId = currency.id,
      categoryId = other.id,
      accountId = card.id,
      date = "2026-08-11",
      title = null,
      notes = null,
      presetId = null,
    )

    val data = dashboardRepo.observe(GUEST_OWNER_ID, "2026-08-01", "2026-08-31").first()

    assertEquals(1, data.balances.size)
    val balance = data.balances.first()
    assertEquals(800L, balance.totalMinor)
    assertEquals(2, balance.accounts.size)
    assertEquals(1000L, balance.accounts.first { it.account.name == "Cash" }.balanceMinor)
    assertEquals(-200L, balance.accounts.first { it.account.name == "Card" }.balanceMinor)

    assertEquals(1, data.monthly.size)
    assertEquals(1000L, data.monthly.first().incomeMinor)
    assertEquals(200L, data.monthly.first().expenseMinor)
    assertEquals(2, data.recent.size)
  }

  @Test
  fun `deleting an account with transactions is blocked`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val cash = accountRepo.observeActive(GUEST_OWNER_ID).first().first()
    transactionRepo.add(
      ownerId = GUEST_OWNER_ID,
      type = TransactionType.INCOME,
      amount = 100,
      currencyId = currency.id,
      categoryId = null,
      accountId = cash.id,
      date = null,
      title = null,
      notes = null,
      presetId = null,
    )
    val thrown =
      try {
        accountRepo.delete(GUEST_OWNER_ID, cash.id)
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `archive then delete works and restore brings account back`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val card = accountRepo.add(GUEST_OWNER_ID, currency.id, "Card")

    accountRepo.archive(GUEST_OWNER_ID, card.id)
    assertEquals(1, accountRepo.observeActive(GUEST_OWNER_ID).first().size) // seeded Cash remains
    assertEquals(1, accountRepo.observeArchived(GUEST_OWNER_ID).first().size)

    accountRepo.restore(GUEST_OWNER_ID, card.id)
    assertEquals(2, accountRepo.observeActive(GUEST_OWNER_ID).first().size)

    accountRepo.delete(GUEST_OWNER_ID, card.id)
    assertEquals(1, accountRepo.observeActive(GUEST_OWNER_ID).first().size)
  }

  @Test
  fun `duplicate currency codes are rejected`() = runTest {
    val thrown =
      try {
        currencyRepo.add(GUEST_OWNER_ID, "USD", "$", "Another Dollar")
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `deleting the default currency promotes the next one and reports it`() = runTest {
    val eur = currencyRepo.add(GUEST_OWNER_ID, "EUR", "€", "Euro")
    val usd = currencyRepo.getAll(GUEST_OWNER_ID).first { it.isDefault }

    val promoted = currencyRepo.delete(GUEST_OWNER_ID, usd.id)

    assertEquals("EUR", promoted)
    assertTrue(currencyRepo.getAll(GUEST_OWNER_ID).first { it.id == eur.id }.isDefault)
  }

  @Test
  fun `duplicate category names are rejected per type`() = runTest {
    val thrown =
      try {
        categoryRepo.add(GUEST_OWNER_ID, "groceries", TransactionType.EXPENSE, "#111111")
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)

    // Same name on the other type is allowed.
    val incomeCat = categoryRepo.add(GUEST_OWNER_ID, "Groceries", TransactionType.INCOME, "#222222")
    assertEquals(TransactionType.INCOME, incomeCat.type)
  }

  @Test
  fun `guest mutations do not enqueue outbox ops`() = runTest {
    currencyRepo.add(GUEST_OWNER_ID, "GBP", "£", "Pound")
    assertEquals(0, db.outboxDao().getAllForOwner(GUEST_OWNER_ID).size)
  }

  @Test
  fun `deleting the last account of a currency is blocked`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val cash = accountRepo.observeActive(GUEST_OWNER_ID).first().first()

    val thrown =
      try {
        accountRepo.delete(GUEST_OWNER_ID, cash.id)
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)
    assertTrue(thrown!!.message!!.contains("needs at least one account"))

    // A second account in the same currency makes deletion legal again.
    val card = accountRepo.add(GUEST_OWNER_ID, currency.id, "Card")
    accountRepo.delete(GUEST_OWNER_ID, cash.id)
    assertEquals(listOf(card.id), accountRepo.observeActive(GUEST_OWNER_ID).first().map { it.id })
  }

  @Test
  fun `archiving the last account of a currency is blocked`() = runTest {
    val currency = currencyRepo.getAll(GUEST_OWNER_ID).first()
    val cash = accountRepo.observeActive(GUEST_OWNER_ID).first().first()

    val thrown =
      try {
        accountRepo.archive(GUEST_OWNER_ID, cash.id)
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertTrue(thrown != null)

    accountRepo.add(GUEST_OWNER_ID, currency.id, "Card")
    accountRepo.archive(GUEST_OWNER_ID, cash.id)
    assertEquals(1, accountRepo.observeArchived(GUEST_OWNER_ID).first().size)
  }

  @Test
  fun `guest seed makes USD the default currency`() = runTest {
    val usd = currencyRepo.getAll(GUEST_OWNER_ID).first { it.code == "USD" }
    assertTrue(usd.isDefault)
  }

  @Test
  fun `signed-in mutations enqueue idempotent ops per mutation`() = runTest {
    val userId = "user-1"
    val added = currencyRepo.add(userId, "GBP", "£", "Pound")
    currencyRepo.add(userId, "EUR", "€", "Euro")
    val ops = db.outboxDao().getAllForOwner(userId)

    // Each currency add is two ops: the currency + its default Cash account.
    assertEquals(4, ops.size)
    assertEquals("currencies", ops.first().tableName)
    assertEquals(com.joeabouserhal.financetracker.data.local.entities.OutboxAction.INSERT, ops.first().action)
    assertTrue(ops.first().payloadJson.contains(added.id))
    assertEquals(2, ops.count { it.tableName == "accounts" })

    currencyRepo.delete(userId, added.id)
    assertEquals(5, db.outboxDao().getAllForOwner(userId).size)
  }

  @Test
  fun `adding a currency creates a default Cash account`() = runTest {
    val gbp = currencyRepo.add(GUEST_OWNER_ID, "GBP", "£", "Pound")
    val accounts = accountRepo.observeByCurrency(GUEST_OWNER_ID, gbp.id).first()

    assertEquals(1, accounts.size)
    assertEquals("Cash", accounts.single().name)
    assertEquals(0, db.outboxDao().getAllForOwner(GUEST_OWNER_ID).size) // guest: no ops
  }
}
