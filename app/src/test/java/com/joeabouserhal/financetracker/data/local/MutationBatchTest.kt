package com.joeabouserhal.financetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.repositories.CurrencyRepository
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MutationBatchTest {
  private lateinit var db: AppDatabase
  @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build() }
  @After fun close() { db.close() }

  @Test fun `nested logical action shares a batch with contiguous immutable indices`() = runTest {
    val repo = CurrencyRepository(db.currencyDao(), db)
    db.withMutation {
      repo.add("user", "USD", "$", "Dollar")
      repo.add("user", "EUR", "E", "Euro", true)
    }
    val ops = db.outboxDao().getAllForOwner("user")
    assertEquals(1, ops.map { it.batchId }.distinct().size)
    assertEquals(ops.indices.toList(), ops.map { it.batchIndex })
    assertEquals(5, ops.size) // Two currencies, two accounts and the previous default.
    assertEquals(1, db.currencyDao().getAll("user").count { it.isDefault })
  }

  @Test fun `failed logical action rolls back finance rows and every outbox entry`() = runTest {
    try {
      db.withMutation {
        CurrencyRepository(db.currencyDao(), db).add("user", "USD", "$", "Dollar")
        error("Interrupted")
      }
    } catch (_: IllegalStateException) { }
    assertTrue(db.currencyDao().getAll("user").isEmpty())
    assertTrue(db.outboxDao().getAllForOwner("user").isEmpty())
  }

  @Test fun `signed-in currency cannot be tombstoned while transactions use it`() = runTest {
    val currencies = CurrencyRepository(db.currencyDao(), db)
    val usd = currencies.add("user", "USD", "$", "Dollar")
    currencies.add("user", "EUR", "E", "Euro")
    db.categoryDao().upsert(com.joeabouserhal.financetracker.data.local.entities.CategoryEntity("other", "user", "Other", com.joeabouserhal.financetracker.data.local.entities.TransactionType.EXPENSE, "#777777", true, "2026-01-01", "2026-01-01"))
    val categories = com.joeabouserhal.financetracker.data.repositories.CategoryRepository(db.categoryDao(), db)
    val transactions = com.joeabouserhal.financetracker.data.repositories.TransactionRepository(db.transactionDao(), db.categoryDao(), categories, db.goalDao(), db)
    transactions.add("user", com.joeabouserhal.financetracker.data.local.entities.TransactionType.EXPENSE, 500, usd.id, "other", db.accountDao().getAll("user").first { it.currencyId == usd.id }.id, null, null, null, null)
    val before = db.outboxDao().getAllForOwner("user").size
    try { currencies.delete("user", usd.id); fail("In-use currency was deleted") } catch (_: IllegalArgumentException) { }
    assertNull(db.currencyDao().getById("user", usd.id)!!.deletedAt)
    assertEquals(before, db.outboxDao().getAllForOwner("user").size)
    try {
      transactions.add("other-user", com.joeabouserhal.financetracker.data.local.entities.TransactionType.EXPENSE, 500, usd.id, "other", null, null, null, null, null)
      fail("Cross-owner references were accepted")
    } catch (_: IllegalArgumentException) { }
    assertTrue(db.transactionDao().getAll("other-user").isEmpty())
  }
}
