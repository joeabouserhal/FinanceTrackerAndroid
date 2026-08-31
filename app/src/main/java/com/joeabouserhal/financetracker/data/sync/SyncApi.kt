package com.joeabouserhal.financetracker.data.sync

import kotlinx.serialization.json.JsonObject

/**
 * Remote sync surface, implemented by Supabase and faked in unit tests.
 *
 * Pull rows come back ordered by (updated_at ASC, key ASC) so the engine can
 * keyset-paginate with the (updated_at, key) watermark stored in sync_meta.
 */
interface SyncApi {
  /** True when a usable authenticated session is available (refreshing if needed). */
  suspend fun ensureAuthenticated(): Boolean

  /** Idempotent push: full-row upsert keyed on the row id. */
  suspend fun upsert(table: String, payload: JsonObject, onConflict: String)

  /** Idempotent push: delete the row with [id] (no-op when absent). */
  suspend fun deleteById(table: String, keyColumn: String, id: String)

  /**
   * Rows for [ownerId] strictly after the (updated_at, key) cursor, ascending.
   * When both cursor fields are null, returns everything for the owner.
   */
  suspend fun pullRows(
    table: String,
    ownerId: String,
    afterUpdatedAt: String?,
    afterId: String?,
    limit: Long = DEFAULT_PAGE_SIZE,
    keyColumn: String,
  ): List<JsonObject>

  companion object {
    const val DEFAULT_PAGE_SIZE: Long = 500
  }
}

/**
 * FK-safe push/pull order: parents before children. Each table declares its
 * row key — most use `id`, but `profiles` is keyed by `user_id`.
 */
object SyncTables {
  data class Spec(
    val name: String,
    val keyColumn: String,
    val conflictColumn: String = keyColumn,
  )

  val ALL: List<Spec> =
    listOf(
      Spec("currencies", "id"),
      Spec("categories", "id"),
      Spec("accounts", "id"),
      Spec("presets", "id"),
      Spec("goals", "id"),
      Spec("transactions", "id"),
      Spec("profiles", "user_id"),
    )
}
