package com.joeabouserhal.financetracker.data.repositories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.sync.SyncMappers
import com.joeabouserhal.financetracker.ui.goals.buildAccountBalances
import com.joeabouserhal.financetracker.utils.OpeningBalance
import com.joeabouserhal.financetracker.utils.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OpeningBalanceTest {
  private lateinit var db: AppDatabase
  private lateinit var currencies: CurrencyRepository
  private lateinit var accounts: AccountRepository
  private lateinit var transactions: TransactionRepository
  private lateinit var dashboard: DashboardRepository
  @Before fun setup() {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    currencies = CurrencyRepository(db.currencyDao(), db)
    accounts = AccountRepository(db.accountDao(), db.transactionDao(), db)
    val categories = CategoryRepository(db.categoryDao(), db)
    transactions = TransactionRepository(db.transactionDao(), db.categoryDao(), categories, db.goalDao(), db)
    dashboard = DashboardRepository(db.currencyDao(), db.accountDao(), db.categoryDao(), db.transactionDao())
  }
  @After fun close() { db.close() }

  @Test fun `currency starting funds belong to Cash without creating income or transactions`() = runTest {
    val currency = currencies.add("owner", "USD", "$", "Dollar", openingBalanceMinor = 123456)
    val cash = db.accountDao().getAll("owner").single()
    assertEquals(123456L, cash.openingBalanceMinor)
    assertTrue(cash.isDefault)
    assertTrue(db.transactionDao().getAll("owner").isEmpty())
    val data = dashboard.observe("owner", "2026-10-01", "2026-10-31").first()
    assertEquals(123456L, data.balances.single().totalMinor)
    assertTrue(data.balances.single().hasActivity)
    assertTrue(data.monthly.isEmpty())
    assertTrue(data.recent.isEmpty())
    val queue = db.outboxDao().observeForOwner("owner").first()
    assertEquals(2, queue.size)
    assertEquals(1, queue.map { it.batchId }.distinct().size)
    assertTrue(queue.none { it.tableName == "transactions" })
    val payload = Json.parseToJsonElement(queue.single { it.tableName == "accounts" }.payloadJson) as JsonObject
    assertEquals(JsonPrimitive(123456), payload["opening_balance"])
    assertEquals(currency.id, cash.currencyId)
  }
  @Test fun `new account opening balance and transfers agree across balance sources`() = runTest {
    val currency = currencies.add(GUEST_OWNER_ID, "USD", "$", "Dollar", openingBalanceMinor = 10000)
    val cash = db.accountDao().getAll(GUEST_OWNER_ID).single()
    val bank = accounts.add(GUEST_OWNER_ID, currency.id, "Bank", openingBalanceMinor = 20000)
    transactions.saveTransfer(GUEST_OWNER_ID, amount = 2500, currencyId = currency.id,
      sourceAccountId = bank.id, destinationAccountId = cash.id, date = "2026-10-23")
    val expected = mapOf(cash.id to 12500L, bank.id to 17500L)
    val allAccounts = db.accountDao().getAll(GUEST_OWNER_ID)
    val rows = db.transactionDao().getAll(GUEST_OWNER_ID)
    assertEquals(expected, accountBalanceMap(rows, allAccounts))
    assertEquals(expected, buildAccountBalances(rows, allAccounts))
    assertEquals(expected, db.transactionDao().observeAccountNets(GUEST_OWNER_ID).first().associate { it.accountId to it.balanceMinor })
    val data = dashboard.observe(GUEST_OWNER_ID, "2026-10-01", "2026-10-31").first()
    assertEquals(30000L, data.balances.single().totalMinor)
    assertTrue(data.monthly.isEmpty())
    assertTrue(db.outboxDao().observeForOwner(GUEST_OWNER_ID).first().isEmpty())
  }
  @Test fun `default zero and overdrafts are optional and never become income or expense`() = runTest {
    val currency = currencies.add("owner", "USD", "$", "Dollar")
    assertEquals(0L, db.accountDao().getAll("owner").single().openingBalanceMinor)
    val bank = accounts.add("owner", currency.id, "Overdraft", -5000)
    assertEquals(-5000L, db.transactionDao().observeAccountNets("owner").first().single { it.accountId == bank.id }.balanceMinor)
    assertEquals(-5000L, dashboard.observe("owner", "2026-10-01", "2026-10-31").first().balances.single().totalMinor)
    assertTrue(db.transactionDao().getAll("owner").isEmpty())
  }
  @Test fun `account edits preserve starting funds and cannot move funded accounts to another currency`() = runTest {
    val usd = currencies.add("owner", "USD", "$", "Dollar", openingBalanceMinor = 10000)
    val eur = currencies.add("owner", "EUR", "€", "Euro")
    val cash = db.accountDao().getAll("owner").first { it.currencyId == usd.id }
    accounts.update("owner", cash.id, "Wallet", usd.id)
    accounts.setDefault("owner", cash.id)
    assertEquals(10000L, db.accountDao().getById("owner", cash.id)?.openingBalanceMinor)
    try { accounts.update("owner", cash.id, "Wallet", eur.id); fail("Funded accounts cannot change currency") }
    catch (_: IllegalArgumentException) { }
  }
  @Test fun `wrong owner currency is rejected without creating a funded account or outbox entries`() = runTest {
    val currency = currencies.add("owner", "USD", "$", "Dollar")
    try { accounts.add("other-owner", currency.id, "Bad account", 10000); fail("Must validate ownership") }
    catch (_: IllegalArgumentException) { }
    assertTrue(db.accountDao().getAll("other-owner").isEmpty())
    assertTrue(db.outboxDao().observeForOwner("other-owner").first().isEmpty())
    assertTrue(db.transactionDao().observeAccountNets("other-owner").first().isEmpty())
  }
  @Test fun `sync maps new and legacy balances without losing precision or accepting malformed amounts`() {
    val row = AccountEntity("id", "owner", "currency", "Cash", createdAt = "now", updatedAt = "now", openingBalanceMinor = 9876543210123456)
    val payload = JsonObject(OutboxWriter.account("owner", row) + ("updated_at" to JsonPrimitive("now")))
    assertEquals(row.openingBalanceMinor, SyncMappers.account(payload).openingBalanceMinor)
    assertEquals(0L, SyncMappers.account(JsonObject(payload - "opening_balance")).openingBalanceMinor)
    assertThrows(IllegalArgumentException::class.java) { SyncMappers.account(JsonObject(payload + ("opening_balance" to JsonPrimitive("not a number")))) }
  }
  @Test fun `opening input is optional exact and range checked`() {
    assertEquals(0L, OpeningBalance.parse(""))
    assertEquals(123456L, OpeningBalance.parse("1234.56"))
    assertEquals(1234L, OpeningBalance.parse("12,34"))
    assertEquals(-1234L, OpeningBalance.parse("-12.34"))
    assertEquals(124L, OpeningBalance.parse("1.235"))
    listOf("coffee", "1+2", "9999999999999999999999").forEach { assertThrows(IllegalArgumentException::class.java) { OpeningBalance.parse(it) } }
    assertEquals("-$92,233,720,368,547,758.08", Money.format(Long.MIN_VALUE, "$"))
  }
}
