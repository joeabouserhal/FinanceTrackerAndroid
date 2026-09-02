package com.joeabouserhal.financetracker.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.data.local.AppDatabase
import com.joeabouserhal.financetracker.data.local.GUEST_OWNER_ID
import com.joeabouserhal.financetracker.data.local.OutboxWriter
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.session.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val USER_ID = "user-1"

/** In-memory stand-in for the Supabase project. */
private class FakeSyncApi(
  var authenticated: Boolean = true,
) : SyncApi {
  val pushed = mutableListOf<Pair<String, JsonObject>>() // table to payload
  val deleted = mutableListOf<Pair<String, String>>() // table to id
  val pullCursors = mutableListOf<Pair<String?, String?>>()
  val server = mutableMapOf<String, MutableList<JsonObject>>()
  var failUpsertFor: String? = null
  var failPullFor: String? = null

  override suspend fun ensureAuthenticated(): Boolean = authenticated

  override suspend fun upsert(table: String, payload: JsonObject, onConflict: String) {
    if (failUpsertFor == table) throw RuntimeException("push boom")
    // Emulate the server-side touch_updated_at() trigger.
    val stored =
      if (payload.containsKey("updated_at")) {
        payload
      } else {
        buildJsonObject {
          payload.forEach { (k, v) -> put(k, v) }
          put("updated_at", "2026-08-23T10:00:00Z")
        }
      }
    val id = stored.id()
    pushed += table to stored
    // Push-order tests intentionally use skeletal payloads. A real Supabase
    // RPC rejects those instead of publishing malformed rows for a later pull.
    val publishable = table != "transactions" || stored.containsKey("type")
    if (publishable) {
      server.getOrPut(table) { mutableListOf() }.removeAll { it.id() == id }
      server.getValue(table).add(stored)
    }
  }

  override suspend fun deleteById(table: String, keyColumn: String, id: String) {
    deleted += table to id
    server[table]?.removeAll { it.str(keyColumn) == id }
  }

  override suspend fun pullRows(
    table: String,
    ownerId: String,
    afterUpdatedAt: String?,
    afterId: String?,
    limit: Long,
    keyColumn: String,
  ): List<JsonObject> {
    if (failPullFor == table) throw RuntimeException("pull boom")
    pullCursors += afterUpdatedAt to afterId
    val mine = server[table].orEmpty().filter { it.str("user_id") == ownerId }
    val filtered =
      if (afterUpdatedAt == null) {
        mine
      } else {
        mine.filter { row ->
          val ts = row.str("updated_at")
          val key = row.str(keyColumn)
          ts > afterUpdatedAt || (ts == afterUpdatedAt && afterId != null && key > afterId)
        }
      }
    // Same keyset order the real API guarantees.
    return filtered
      .sortedWith(compareBy({ it.str("updated_at") }, { it.str(keyColumn) }))
      .take(limit.toInt())
  }

  fun seedCurrency(id: String, name: String, updatedAt: String) {
    server.getOrPut("currencies") { mutableListOf() }.add(
      buildJsonObject {
        put("id", id)
        put("user_id", USER_ID)
        put("code", id.uppercase())
        put("symbol", "$")
        put("name", name)
        put("is_default", true)
        put("created_at", updatedAt)
        put("updated_at", updatedAt)
      },
    )
  }

  fun removeCurrency(id: String) {
    server["currencies"]?.removeAll { it.id() == id }
  }

  private fun JsonObject.id(): String = str("id")

  private fun JsonObject.str(key: String): String =
    (this[key] as JsonPrimitive).contentOrNull ?: error("missing $key")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SyncEngineTest {
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

  private fun engine(api: SyncApi?): SyncEngine = SyncEngine(db, flowOf(Session(USER_ID)), api)

  private fun currencyPayload(id: String, name: String): JsonObject =
    buildJsonObject {
      put("id", id)
      put("user_id", USER_ID)
      put("code", "USD")
      put("symbol", "$")
      put("name", name)
      put("is_default", true)
      put("created_at", "2026-08-23T10:00:00Z")
    }

  @Test
  fun `guest is always skipped and never touches the api`() = runTest {
    val api = FakeSyncApi()
    val engine = SyncEngine(db, flowOf(Session(GUEST_OWNER_ID)), api)

    val outcome = engine.sync()

    assertEquals(SyncOutcome.Skipped(SyncOutcome.SkipReason.GUEST), outcome)
    assertTrue(api.pushed.isEmpty())
    assertTrue(api.pullCursors.isEmpty())
  }

  @Test
  fun `missing api skips without network`() = runTest {
    val outcome = engine(null).sync()
    assertEquals(SyncOutcome.Skipped(SyncOutcome.SkipReason.NOT_CONFIGURED), outcome)
  }

  @Test
  fun `missing session skips`() = runTest {
    val api = FakeSyncApi(authenticated = false)
    val outcome = engine(api).sync()
    assertEquals(SyncOutcome.Skipped(SyncOutcome.SkipReason.NO_SESSION), outcome)
  }

  @Test
  fun `outbox drains in FK order and removes successful ops`() = runTest {
    val api = FakeSyncApi()
    db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "transactions", OutboxAction.INSERT, buildJsonObject { put("id", "tx-1"); put("user_id", USER_ID) }))
    db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "currencies", OutboxAction.INSERT, currencyPayload("cur-1", "USD")))

    val outcome = engine(api).sync()

    assertTrue(outcome is SyncOutcome.Completed)
    assertTrue((outcome as SyncOutcome.Completed).isClean)
    assertEquals(listOf("currencies", "transactions"), api.pushed.map { it.first })
    assertTrue(db.outboxDao().getAllForOwner(USER_ID).isEmpty())
  }

  @Test
  fun `failed ops keep their place and bump attempts`() = runTest {
    val api = FakeSyncApi().apply { failUpsertFor = "transactions" }
    db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "transactions", OutboxAction.INSERT, buildJsonObject { put("id", "tx-1"); put("user_id", USER_ID) }))
    db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "currencies", OutboxAction.INSERT, currencyPayload("cur-1", "USD")))

    val outcome = engine(api).sync() as SyncOutcome.Completed

    assertTrue(!outcome.isClean)
    assertEquals(1, outcome.failedOps)
    val remaining = db.outboxDao().getAllForOwner(USER_ID)
    assertEquals(listOf("transactions"), remaining.map { it.tableName })
    assertEquals(1, remaining.single().attempts)
  }

  @Test
  fun `first pull imports server rows in FK order and stores the watermark`() = runTest {
    val api = FakeSyncApi()
    api.seedCurrency("cur-1", "US Dollar", "2026-08-23T10:00:00Z")

    val outcome = engine(api).sync() as SyncOutcome.Completed

    assertTrue(outcome.isClean)
    assertEquals(1, outcome.pulled)
    assertEquals("US Dollar", db.currencyDao().getById(USER_ID, "cur-1")?.name)
    val meta = db.syncMetaDao().get(USER_ID, "currencies")
    assertEquals("2026-08-23T10:00:00Z", meta?.lastSyncAt)
    assertEquals("cur-1", meta?.lastSyncId)
  }

  @Test
  fun `second pull resumes from the stored watermark cursor`() = runTest {
    val api = FakeSyncApi()
    api.seedCurrency("cur-1", "US Dollar", "2026-08-23T10:00:00Z")
    engine(api).sync()

    api.seedCurrency("cur-2", "Euro", "2026-08-23T11:00:00Z")
    engine(api).sync()

    assertEquals(
      "2026-08-23T10:00:00Z" to "cur-1",
      api.pullCursors.last { it.first != null },
    )
    assertEquals(2, db.currencyDao().getAll(USER_ID).size)
    val meta = db.syncMetaDao().get(USER_ID, "currencies")
    assertEquals("2026-08-23T11:00:00Z", meta?.lastSyncAt)
    assertEquals("cur-2", meta?.lastSyncId)
  }

  @Test
  fun `last write wins - local newer row is kept, remote newer row is applied`() = runTest {
    val api = FakeSyncApi()
    api.seedCurrency("cur-1", "Old name", "2026-08-23T10:00:00Z")

    // Local edit made after the server row but not yet pushed.
    db.currencyDao().upsert(
      CurrencyEntity(
        id = "cur-1",
        ownerId = USER_ID,
        code = "USD",
        symbol = "$",
        name = "Local newer",
        isDefault = true,
        createdAt = "2026-08-23T09:00:00Z",
        updatedAt = "2026-08-23T12:00:00Z",
      ),
    )

    engine(api).sync()
    assertEquals("Local newer", db.currencyDao().getById(USER_ID, "cur-1")?.name)

    // A later server-side edit wins the next round.
    api.removeCurrency("cur-1")
    api.seedCurrency("cur-1", "Remote newer", "2026-08-23T13:00:00Z")

    engine(api).sync()
    assertEquals("Remote newer", db.currencyDao().getById(USER_ID, "cur-1")?.name)
  }

  @Test
  fun `profiles table syncs with user_id as its key column`() = runTest {
    val api = FakeSyncApi()
    api.server.getOrPut("profiles") { mutableListOf() }.add(
      buildJsonObject {
        put("user_id", USER_ID)
        put("name", "Me")
        put("updated_at", "2026-08-23T10:00:00Z")
      },
    )

    val outcome = engine(api).sync() as SyncOutcome.Completed

    assertTrue(outcome.isClean)
    assertEquals("Me", db.profileDao().get(USER_ID)?.name)
    val meta = db.syncMetaDao().get(USER_ID, "profiles")
    assertEquals("2026-08-23T10:00:00Z", meta?.lastSyncAt)
    assertEquals(USER_ID, meta?.lastSyncId)
  }

  @Test
  fun `pull failure is reported while other tables still sync`() = runTest {
    val api = FakeSyncApi().apply { failPullFor = "accounts" }
    api.seedCurrency("cur-1", "US Dollar", "2026-08-23T10:00:00Z")

    val outcome = engine(api).sync() as SyncOutcome.Completed

    assertTrue(outcome.pullFailed)
    assertEquals(1, outcome.pulled)
    assertEquals("US Dollar", db.currencyDao().getById(USER_ID, "cur-1")?.name)
    // Failed table's watermark must not advance.
    assertEquals(null, db.syncMetaDao().get(USER_ID, "accounts"))
  }

  @Test
  fun `permanently failing op is given up after the attempt cap and no longer blocks retries`() = runTest {
    val api = FakeSyncApi().apply { failUpsertFor = "transactions" }
    val opId =
      db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "transactions", OutboxAction.INSERT, buildJsonObject { put("id", "tx-1"); put("user_id", USER_ID) }))
    db.outboxDao().updateAttempts(opId, SyncEngine.MAX_PUSH_ATTEMPTS)

    val outcome = engine(api).sync() as SyncOutcome.Completed

    // Dead op: counted, kept locally, but the run is clean (worker won't retry).
    assertEquals(1, outcome.deadOps)
    assertEquals(0, outcome.failedOps)
    assertTrue(outcome.isClean)
    assertEquals(1, db.outboxDao().getAllForOwner(USER_ID).size)

    // A later successful op still flows through.
    api.failUpsertFor = null
    db.outboxDao().insert(OutboxWriter.newOp(USER_ID, "currencies", OutboxAction.INSERT, currencyPayload("cur-9", "USD")))
    val second = engine(api).sync() as SyncOutcome.Completed
    assertEquals(0, second.failedOps)
    assertEquals(1, second.pushed)
    assertEquals("cur-9", (api.pushed.last().second["id"] as JsonPrimitive).contentOrNull)
    assertEquals(1, db.outboxDao().getAllForOwner(USER_ID).size) // only the dead op remains
  }
}
