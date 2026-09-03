package com.joeabouserhal.financetracker.data.local

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joeabouserhal.financetracker.data.sync.SyncTables
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One-time, transactional recovery from v1 RPC's false insert acknowledgments. */
object SyncRecoveryMigration {
  fun migrate(db: SupportSQLiteDatabase) {
    // Older upgraded installations can lack this index despite v10's entity
    // declaration. Normalize it before Room validates the recovered schema.
    db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_account_id ON goals(account_id)")
    db.execSQL("CREATE TABLE IF NOT EXISTS sync_health(owner_id TEXT NOT NULL PRIMARY KEY, last_success_at TEXT, last_error TEXT, error_kind TEXT)")
    db.execSQL("ALTER TABLE outbox ADD COLUMN last_error TEXT")
    db.execSQL("ALTER TABLE outbox ADD COLUMN error_kind TEXT")
    db.execSQL("ALTER TABLE outbox ADD COLUMN last_attempt_at TEXT")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_outbox_op_id ON outbox(op_id)")

    val pendingVersions = mutableMapOf<Triple<String, String, String>, String>()
    db.query("SELECT id, op_id, owner_id, table_name, payload_json FROM outbox ORDER BY id").use { cursor ->
      while (cursor.moveToNext()) {
        val payload = runCatching { Json.parseToJsonElement(cursor.getString(4)).jsonObject }.getOrNull()
        val table = cursor.getString(3)
        val id = payload?.get(if (table == "profiles") "user_id" else "id")?.jsonPrimitive?.contentOrNull
        val version = payload?.get("sync_version")?.jsonPrimitive?.contentOrNull
        if (id != null && version != null) pendingVersions[Triple(cursor.getString(2), table, id)] = normalize(version)
        if (runCatching { UUID.fromString(cursor.getString(1)) }.isFailure) {
          db.execSQL("UPDATE outbox SET op_id=? WHERE id=?", arrayOf<Any>(UUID.randomUUID().toString(), cursor.getLong(0)))
        }
      }
    }

    // Replay current signed-in partitions with their ORIGINAL versions, never
    // elevate old snapshots to a new edit. Newer server winners and tombstones
    // remain authoritative. Preserve every existing op and every guest row.
    for (spec in SyncTables.ALL) {
      db.query("SELECT * FROM `${spec.name}` WHERE owner_id<>?", arrayOf(GUEST_OWNER_ID)).use { cursor ->
        while (cursor.moveToNext()) {
          val owner = cursor.getString(cursor.getColumnIndexOrThrow("owner_id"))
          val id = if (spec.name == "profiles") owner else cursor.getString(cursor.getColumnIndexOrThrow("id"))
          val storedVersion = cursor.getString(cursor.getColumnIndexOrThrow("sync_version"))
          val updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"))
          val version = maxOf(normalize(storedVersion.ifBlank { updatedAt }), pendingVersions[Triple(owner, spec.name, id)].orEmpty())
          val payload = buildJsonObject {
            for (column in cursor.columnNames) {
              val index = cursor.getColumnIndexOrThrow(column)
              val key = if (column == "owner_id") "user_id" else column
              when {
                column == "sync_version" -> put(key, version)
                cursor.isNull(index) -> put(key, JsonNull)
                column in setOf("is_default", "archived", "completed") -> put(key, cursor.getInt(index) != 0)
                column == "type" -> put(key, cursor.getString(index).lowercase(Locale.ROOT))
                cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER -> put(key, cursor.getLong(index))
                else -> put(key, cursor.getString(index))
              }
            }
          }
          val deleted = payload["deleted_at"]?.let { it != JsonNull } == true
          db.execSQL(
            "INSERT INTO outbox(op_id,owner_id,table_name,action,payload_json,created_at,attempts) VALUES(?,?,?,?,?,?,0)",
            arrayOf(UUID.randomUUID().toString(), owner, spec.name, if (deleted) "DELETE" else "UPDATE", payload.toString(), Instant.now().toString()),
          )
          val localKey = if (spec.name == "profiles") "owner_id" else "id"
          db.execSQL("UPDATE `${spec.name}` SET sync_version=? WHERE owner_id=? AND `$localKey`=?", arrayOf(version, owner, id))
        }
      }
    }
    // Retry old exhausted operations and re-read canonical rows/tombstones.
    db.execSQL("DELETE FROM sync_meta WHERE owner_id<>?", arrayOf(GUEST_OWNER_ID))
  }

  private fun normalize(value: String): String =
    if (value.length > 20 && value[19] == '-') value
    else String.format(Locale.ROOT, "%019d-000000-recovery", runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L))
}
