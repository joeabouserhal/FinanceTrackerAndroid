package com.joeabouserhal.financetracker.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import com.joeabouserhal.financetracker.data.local.entities.OutboxAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * PostgREST-backed [SyncApi]. Every request is scoped by RLS (auth.uid() =
 * user_id), so the explicit user_id filter on pulls is defense-in-depth, not
 * the security boundary.
 */
class SupabaseSyncApi(private val client: SupabaseClient?) : SyncApi {

  private fun requireClient(): SupabaseClient =
    client ?: error("Supabase is not configured — sync should have been skipped")

  override suspend fun ensureAuthenticated(): Boolean {
    val c = client ?: return false
    return try {
      if (c.auth.currentSessionOrNull() == null) c.auth.loadFromStorage()
      if (c.auth.currentSessionOrNull() == null) c.auth.refreshCurrentSession()
      c.auth.currentSessionOrNull() != null
    } catch (e: Exception) {
      android.util.Log.w("SyncEngine", "ensureAuthenticated failed: ${e.message}", e)
      false
    }
  }

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
  ) {
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
    requireClient().postgrest.rpc("apply_sync_mutation", params)
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
