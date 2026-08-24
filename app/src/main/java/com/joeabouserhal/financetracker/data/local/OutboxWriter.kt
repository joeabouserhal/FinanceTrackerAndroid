package com.joeabouserhal.financetracker.data.local

import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializes local rows into Supabase-shaped JSON payloads (snake_case,
 * lowercase enums, ISO timestamps) for the outbox. Manual mapping on purpose:
 * no reflection, and the shape is explicit.
 */
object OutboxWriter {

  /**
   * Enqueue a mutation op inside the same Room transaction as the mutation.
   * Guest rows are never synced, so guest ops are skipped entirely — otherwise
   * the guest partition's queue would grow forever.
   */
  suspend fun enqueue(
    db: AppDatabase,
    ownerId: String,
    table: String,
    action: OutboxAction,
    payload: JsonObject,
  ) {
    if (ownerId == GUEST_OWNER_ID) return
    db.outboxDao().insert(newOp(ownerId, table, action, payload))
    // Tell the Application layer a mutation just happened → immediate sync.
    com.joeabouserhal.financetracker.data.sync.SyncRequests.notifyMutation()
  }

  fun newOp(ownerId: String, table: String, action: OutboxAction, payload: JsonObject): OutboxEntity =
    OutboxEntity(
      ownerId = ownerId,
      tableName = table,
      action = action,
      payloadJson = payload.toString(),
      createdAt = Instant.now().toString(),
    )

  fun currency(ownerId: String, e: CurrencyEntity): JsonObject =
    merge(
      base(e.id, ownerId),
      json(
        "code" to JsonPrimitive(e.code),
        "symbol" to JsonPrimitive(e.symbol),
        "name" to JsonPrimitive(e.name),
        "is_default" to JsonPrimitive(e.isDefault),
      ),
    )

  fun category(ownerId: String, e: CategoryEntity): JsonObject =
    merge(
      base(e.id, ownerId),
      json(
        "name" to JsonPrimitive(e.name),
        "type" to JsonPrimitive(e.type.name.lowercase()),
        "color" to JsonPrimitive(e.color),
        "is_default" to JsonPrimitive(e.isDefault),
      ),
    )

  fun account(ownerId: String, e: AccountEntity): JsonObject =
    merge(
      base(e.id, ownerId),
      json(
        "currency_id" to JsonPrimitive(e.currencyId),
        "name" to JsonPrimitive(e.name),
        "archived" to JsonPrimitive(e.archived),
        "is_default" to JsonPrimitive(e.isDefault),
      ),
    )

  fun preset(ownerId: String, e: PresetEntity): JsonObject =
    merge(
      base(e.id, ownerId),
      json(
        "name" to JsonPrimitive(e.name),
        "type" to JsonPrimitive(e.type.name.lowercase()),
        "default_amount" to (e.defaultAmount?.let { JsonPrimitive(it) } ?: JsonNull),
        "default_currency_id" to (e.defaultCurrencyId?.let { JsonPrimitive(it) } ?: JsonNull),
        "default_category_id" to (e.defaultCategoryId?.let { JsonPrimitive(it) } ?: JsonNull),
        "default_account_id" to (e.defaultAccountId?.let { JsonPrimitive(it) } ?: JsonNull),
        "archived" to JsonPrimitive(e.archived),
      ),
    )

  fun transaction(ownerId: String, e: TransactionEntity): JsonObject =
    merge(
      base(e.id, ownerId),
      json(
        "type" to JsonPrimitive(e.type.name.lowercase()),
        "amount" to JsonPrimitive(e.amount),
        "currency_id" to JsonPrimitive(e.currencyId),
        "category_id" to JsonPrimitive(e.categoryId),
        "account_id" to (e.accountId?.let { JsonPrimitive(it) } ?: JsonNull),
        "date" to JsonPrimitive(e.date),
        "title" to (e.title?.let { JsonPrimitive(it) } ?: JsonNull),
        "notes" to (e.notes?.let { JsonPrimitive(it) } ?: JsonNull),
        "preset_id" to (e.presetId?.let { JsonPrimitive(it) } ?: JsonNull),
      ),
    )

  fun profile(ownerId: String, e: ProfileEntity): JsonObject =
    json(
      "user_id" to JsonPrimitive(ownerId),
      "name" to JsonPrimitive(e.name),
      "created_at" to JsonPrimitive(e.createdAt),
    )

  fun deletePayload(id: String): JsonObject = json("id" to JsonPrimitive(id))

  private fun base(id: String, ownerId: String): JsonObject =
    json(
      "id" to JsonPrimitive(id),
      "user_id" to JsonPrimitive(ownerId),
      "created_at" to JsonPrimitive(Instant.now().toString()),
    )

  private fun merge(a: JsonObject, b: JsonObject): JsonObject = JsonObject(a.toMap() + b.toMap())

  private fun json(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(pairs.toMap())
}
