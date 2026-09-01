package com.joeabouserhal.financetracker.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import java.util.UUID
import kotlinx.coroutines.flow.first
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
class DaoTest {
  private lateinit var db: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  private fun id() = UUID.randomUUID().toString()

  @Test
  fun `guest seeding is idempotent and seeds defaults`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)

    assertEquals(1, db.currencyDao().getAll(GUEST_OWNER_ID).size)
    assertEquals(1, db.accountDao().getAll(GUEST_OWNER_ID).size)
    assertEquals(12, db.categoryDao().getAll(GUEST_OWNER_ID).size)
  }

  @Test
  fun `queries are scoped by owner`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    val userId = "user-1"
    db.currencyDao().upsert(
      CurrencyEntity(id = id(), ownerId = userId, code = "EUR", symbol = "€", name = "Euro", isDefault = true, createdAt = "now", updatedAt = "now"),
    )

    val guestCurrencies = db.currencyDao().observeAll(GUEST_OWNER_ID).first()
    val userCurrencies = db.currencyDao().observeAll(userId).first()

    assertEquals(listOf("USD"), guestCurrencies.map { it.code })
    assertEquals(listOf("EUR"), userCurrencies.map { it.code })
  }

  @Test
  fun `deleting a currency cascades to its accounts`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    val currency = db.currencyDao().getAll(GUEST_OWNER_ID).first()

    db.currencyDao().delete(GUEST_OWNER_ID, currency.id)

    assertEquals(0, db.accountDao().getAll(GUEST_OWNER_ID).size)
  }

  @Test
  fun `deleting a currency used by transactions is restricted`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    val currency = db.currencyDao().getAll(GUEST_OWNER_ID).first()
    val category = db.categoryDao().getAll(GUEST_OWNER_ID).first()
    val account = db.accountDao().getAll(GUEST_OWNER_ID).first()
    db.transactionDao().upsert(
      TransactionEntity(
        id = id(),
        ownerId = GUEST_OWNER_ID,
        type = TransactionType.EXPENSE,
        amount = 100,
        currencyId = currency.id,
        categoryId = category.id,
        accountId = account.id,
        date = "2026-08-23",
        title = null,
        notes = null,
        presetId = null,
        goalId = null,
        createdAt = "now",
        updatedAt = "now",
      ),
    )

    val thrown =
      try {
        db.currencyDao().delete(GUEST_OWNER_ID, currency.id)
        null
      } catch (e: android.database.sqlite.SQLiteConstraintException) {
        e
      }
    assertTrue(thrown != null)
  }

  @Test
  fun `observeRecent respects limit`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)
    val currency = db.currencyDao().getAll(GUEST_OWNER_ID).first()
    val category = db.categoryDao().getAll(GUEST_OWNER_ID).first()
    val account = db.accountDao().getAll(GUEST_OWNER_ID).first()

    repeat(3) { i ->
      db.transactionDao().upsert(
        TransactionEntity(
          id = id(),
          ownerId = GUEST_OWNER_ID,
          type = TransactionType.INCOME,
          amount = 100L + i,
          currencyId = currency.id,
          categoryId = category.id,
          accountId = account.id,
          date = "2026-08-2$i",
          title = null,
          notes = null,
          presetId = null,
          goalId = null,
          createdAt = "now",
          updatedAt = "now",
        ),
      )
    }

    assertEquals(2, db.transactionDao().observeRecent(GUEST_OWNER_ID, 2).first().size)
  }

  @Test
  fun `categories are stored with type and color`() = runTest {
    GuestSeeder.seedGuestDefaults(db, GUEST_OWNER_ID)

    val other = db.categoryDao().getDefaultOther(GUEST_OWNER_ID, TransactionType.EXPENSE)
    assertTrue(other != null)
    assertEquals("#77746C", other!!.color)
  }
}
