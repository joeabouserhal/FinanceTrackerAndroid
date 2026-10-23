package com.joeabouserhal.financetracker.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException

/**
 * PostgREST-backed [SyncApi]. Every request is scoped by RLS (auth.uid() =
 * user_id), so the explicit user_id filter on pulls is defense-in-depth, not
 * the security boundary.
 */
class SupabaseSyncApi(private val client: SupabaseClient?) : SyncApi {
  override suspend fun applyBatch(operations: List<com.joeabouserhal.financetracker.data.local.entities.OutboxEntity>): List<MutationResult?> {
    require(operations.isNotEmpty() && operations.map { it.batchId }.distinct().size == 1)
    val input = JsonArray(operations.map { op -> JsonObject(mapOf(
      "operation_id" to JsonPrimitive(op.opId), "table" to JsonPrimitive(op.tableName),
      "action" to JsonPrimitive(op.action.name.lowercase()),
      "payload" to kotlinx.serialization.json.Json.parseToJsonElement(op.payloadJson),
    )) })
    val response = requireClient().postgrest.rpc("apply_sync_batch", JsonObject(mapOf(
      "p_batch_id" to JsonPrimitive(operations.first().batchId), "p_operations" to input,
    ))).decodeAs<JsonObject>()
    check(response["protocol"] == JsonPrimitive(3) && response["batch_id"] == JsonPrimitive(operations.first().batchId)) { "Unverified batch acknowledgment" }
    val results = response["results"] as? JsonArray ?: error("Missing batch results")
    check(results.size == operations.size) { "Incomplete batch acknowledgment" }
    return results.mapIndexed { index, element ->
      val result = element as? JsonObject ?: error("Invalid batch result")
      val op = operations[index]
      val payload = kotlinx.serialization.json.Json.parseToJsonElement(op.payloadJson) as JsonObject
      val key = if (op.tableName == "profiles") "user_id" else "id"
      val status = result["status"]?.jsonPrimitive?.contentOrNull
      check(result["operation_id"] == JsonPrimitive(op.opId) && result["table"] == JsonPrimitive(op.tableName) && result["id"] == payload[key]) { "Batch acknowledgment identity mismatch" }
      check(status in setOf("applied", "stale", "deleted", "superseded")) { "Invalid batch acknowledgment status" }
      val row = result["row"]?.takeIf { it != JsonNull } as? JsonObject
      check(row != null || status in setOf("deleted", "superseded")) { "Missing canonical row" }
      MutationResult(status!!, row)
    }
  }

  private fun requireClient(): SupabaseClient =
    client ?: error("Supabase is not configured — sync should have been skipped")

  override suspend fun ensureAuthenticated(): Boolean {
    val c = client ?: return false
    return try {
      if (c.auth.currentSessionOrNull() == null) c.auth.loadFromStorage()
      val session = c.auth.currentSessionOrNull() ?: return false
      if (session.expiresAt.epochSeconds <= System.currentTimeMillis() / 1000 + 60) c.auth.refreshCurrentSession()
      c.auth.currentSessionOrNull() != null
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      android.util.Log.w("SyncEngine", "ensureAuthenticated failed: ${e.message}", e)
      throw e
    }
  }

  override suspend fun ensureAuthenticatedFor(ownerId: String): Boolean =
    ensureAuthenticated() && requireClient().auth.currentUserOrNull()?.id == ownerId

  override suspend fun upsert(table: String, payload: JsonObject, onConflict: String) {
    requireClient().postgrest.from(table).upsert(JsonArray(listOf(payload))) {
      this.onConflict = onConflict
    }
  }

  override suspend fun deleteById(table: String, keyColumn: String, id: String) {
    requireClient().postgrest.from(table).delete {
      filter { eq(keyColumn, id) }
    }
  }

  /** Server-side ownership checks, version comparison, and operation-id dedupe. */
  override suspend fun applyMutation(
    table: String,
    action: OutboxAction,
    payload: JsonObject,
    keyColumn: String,
    conflictColumn: String,
    operationId: String,
  ): MutationResult {
    val params =
      JsonObject(
        mapOf(
          "p_table" to JsonPrimitive(table),
          "p_action" to JsonPrimitive(action.name.lowercase()),
          "p_payload" to payload,
          "p_version" to (payload["sync_version"] ?: JsonPrimitive("")),
          "p_operation_id" to JsonPrimitive(operationId),
        ),
      )
    val result = requireClient().postgrest.rpc("apply_sync_mutation", params).decodeAs<JsonObject>()
    val status = result["status"]?.jsonPrimitive?.contentOrNull
    check(result["protocol"]?.jsonPrimitive?.contentOrNull == "2" && status in setOf("applied", "stale", "deleted")) {
      "Unverified sync acknowledgment"
    }
    check(result["id"] == payload[keyColumn] && result["table"] == JsonPrimitive(table)) { "Sync acknowledgment identity mismatch" }
    val row = result["row"]?.takeIf { it != JsonNull } as? JsonObject
    check(status == "deleted" || row != null) { "Sync acknowledgment has no canonical row" }
    return MutationResult(status!!, row)
  }

  override suspend fun pullRows(
    table: String,
    ownerId: String,
    afterUpdatedAt: String?,
    afterId: String?,
    limit: Long,
    keyColumn: String,
  ): List<JsonObject> {
    val result =
      requireClient().postgrest.from(table).select {
        filter {
          eq("user_id", ownerId)
          if (afterUpdatedAt != null) {
            or {
              gt("updated_at", afterUpdatedAt)
              if (afterId != null) {
                and {
                  eq("updated_at", afterUpdatedAt)
                  gt(keyColumn, afterId)
                }
              }
            }
          }
        }
        order("updated_at", Order.ASCENDING)
        order(keyColumn, Order.ASCENDING)
        limit(limit)
      }
    return result.decodeList<JsonObject>()
  }
}
