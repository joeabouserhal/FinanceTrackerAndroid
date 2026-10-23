package com.joeabouserhal.financetracker.data.recurring

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.local.*
import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.data.local.seed.GuestSeeder
import com.joeabouserhal.financetracker.data.repositories.*
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecurringRepositoryTest {
  private lateinit var db: AppDatabase
  private lateinit var transactions: TransactionRepository
  private lateinit var recurring: RecurringRepository
  private lateinit var preset: PresetEntity
  private val owner = GUEST_OWNER_ID
  private val date = "2026-03-29"
  private val now = Instant.parse("2026-03-29T12:00:00Z")
  private val zone = ZoneId.of("Europe/Berlin")
  private fun draft() = RecurrenceDraft(RecurrenceFrequency.DAILY, startDate = date, endDate = date, timeMinutes = 540)

  @Before fun setup() = runBlocking {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    GuestSeeder.seedGuestDefaults(db, owner)
    val categories = CategoryRepository(db.categoryDao(), db)
    transactions = TransactionRepository(db.transactionDao(), db.categoryDao(), categories, db.goalDao(), db)
    recurring = RecurringRepository(db, transactions)
    val currency = db.currencyDao().getAll(owner).first()
    val account = db.accountDao().getAll(owner).first { it.currencyId == currency.id }
    preset = PresetRepository(db.presetDao(), db).add(owner, "Coffee", TransactionType.EXPENSE, 1200, currency.id,
      db.categoryDao().getDefaultOther(owner, TransactionType.EXPENSE)!!.id, account.id)
  }
  @After fun close() { db.close() }

  @Test fun `editing and pausing preserve occurrence history and never post twice`() = runTest {
    val schedule = recurring.saveSchedule(owner, preset.id, draft())
    val due = recurring.postDue(owner, now, zone).single()
    val transaction = db.transactionDao().getAll(owner).single()
    recurring.saveSchedule(owner, preset.id, draft().copy(timeMinutes = 600))
    recurring.setPaused(owner, schedule.id, true)
    recurring.setPaused(owner, schedule.id, false)
    assertNotNull(db.recurringOccurrenceDao().getById(owner, due.occurrenceId))
    assertTrue(RecurringRepository(db, transactions).postDue(owner, now, zone).isEmpty())
    assertEquals(transaction.id, db.transactionDao().getAll(owner).single().id)
    assertTrue(db.outboxDao().getAllForOwner(owner).isEmpty())
  }

  @Test fun `snooze uses a new deterministic generation and stale verify cannot consume it`() = runTest {
    recurring.saveSchedule(owner, preset.id, draft())
    val due = recurring.postDue(owner, now, zone).single()
    val first = db.transactionDao().getAll(owner).single()
    // Use a real future instant because notification snoozes reject past times.
    val until = Instant.now().plusSeconds(3600)
    recurring.snooze(owner, due.occurrenceId, until)
    recurring.verify(owner, due.occurrenceId)
    assertEquals(RecurringOccurrenceState.SNOOZED, db.recurringOccurrenceDao().getById(owner, due.occurrenceId)!!.state)
    assertTrue(db.transactionDao().getAll(owner).isEmpty())
    recurring.postDue(owner, until.plusSeconds(1), zone)
    val next = db.transactionDao().getAll(owner).single()
    assertNotEquals(first.id, next.id)
    assertEquals(1, next.recurrenceGeneration)
    assertTrue(recurring.postDue(owner, until.plusSeconds(2), zone).isEmpty())
  }

  @Test fun `lost occurrence history reuses existing generated transaction`() = runTest {
    recurring.saveSchedule(owner, preset.id, draft())
    recurring.postDue(owner, now, zone)
    val original = db.transactionDao().getAll(owner).single()
    db.openHelper.writableDatabase.execSQL("DELETE FROM recurring_occurrences")
    recurring.postDue(owner, now, zone)
    assertEquals(original.id, db.transactionDao().getAll(owner).single().id)
  }

  @Test fun `skip never overwrites a posted occurrence`() = runTest {
    val schedule = recurring.saveSchedule(owner, preset.id, draft())
    val posted = recurring.postDue(owner, now, zone).single()
    assertFalse(recurring.skipNext(owner, schedule.id, LocalDate.parse(date)))
    assertEquals(RecurringOccurrenceState.POSTED, db.recurringOccurrenceDao().getById(owner, posted.occurrenceId)!!.state)
  }

  @Test fun `scheduled time stays at nine across daylight saving transitions`() {
    for (day in listOf("2026-03-29", "2026-10-25")) {
      val instant = recurringTime(LocalDate.parse(day), 540, zone)
      assertEquals(LocalTime.of(9, 0), instant.atZone(zone).toLocalTime())
    }
    val gap = recurringTime(LocalDate.parse("2026-03-29"), 150, zone).atZone(zone)
    assertEquals(LocalTime.of(3, 30), gap.toLocalTime())
  }

  @Test fun `invalid preset is paused without deleting past occurrences`() = runTest {
    val schedule = recurring.saveSchedule(owner, preset.id, draft())
    val due = recurring.postDue(owner, now, zone).single()
    db.presetDao().archive(owner, preset.id, now.toString())
    recurring.postDue(owner, now, zone)
    assertTrue(db.recurringScheduleDao().getById(owner, schedule.id)!!.paused)
    assertNotNull(db.recurringOccurrenceDao().getById(owner, due.occurrenceId))
  }
}
