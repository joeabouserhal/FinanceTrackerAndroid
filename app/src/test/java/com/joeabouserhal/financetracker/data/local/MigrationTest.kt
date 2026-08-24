package com.joeabouserhal.financetracker.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room migration scaffolding. Runs the REAL [Migrations] against databases
 * built from the exported KSP schema JSONs (app/schemas), then verifies data
 * preservation and that the resulting tables match the latest exported schema.
 *
 * Room's own MigrationTestHelper cannot run under Robolectric with Room 2.8.x
 * (SupportSQLiteDriver name-vs-path mismatch), so this test drives the
 * framework SQLite directly — the migrations under test are the real objects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MigrationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `migrate 1 to 3 preserves data and backfills updated_at`() {
    val db = openVersion(1)
    db.execSQL(
      "INSERT INTO currencies (id, owner_id, code, symbol, name, is_default, created_at) " +
        "VALUES ('cur-1', 'user-1', 'USD', '$', 'US Dollar', 1, '2026-08-01T10:00:00Z')",
    )
    db.execSQL(
      "INSERT INTO categories (id, owner_id, name, type, color, is_default, created_at) " +
        "VALUES ('cat-1', 'user-1', 'Other', 'expense', '#77746C', 1, '2026-08-01T10:00:00Z')",
    )
    db.execSQL(
      "INSERT INTO transactions (id, owner_id, type, amount, currency_id, category_id, account_id, date, title, notes, preset_id, created_at, updated_at) " +
        "VALUES ('tx-1', 'user-1', 'expense', 500, 'cur-1', 'cat-1', NULL, '2026-08-01', 'Coffee', NULL, NULL, '2026-08-01T10:00:00Z', '2026-08-01T10:00:00Z')",
    )
    db.execSQL(
      "INSERT INTO sync_meta (owner_id, table_name, last_sync_at) VALUES ('user-1', 'currencies', '2026-08-01T10:00:00Z')",
    )
    db.execSQL(
      "INSERT INTO outbox (owner_id, table_name, action, payload_json, created_at, attempts) " +
        "VALUES ('user-1', 'currencies', 'INSERT', '{}', '2026-08-01T10:00:00Z', 0)",
    )

    Migrations.MIGRATION_1_2.migrate(db)
    Migrations.MIGRATION_2_3.migrate(db)

    db.query("SELECT code, updated_at FROM currencies WHERE id = 'cur-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("USD", cursor.getString(0))
      // v1 rows had no updated_at; MIGRATION_2_3 backfills it from created_at.
      assertEquals("2026-08-01T10:00:00Z", cursor.getString(1))
    }
    db.query("SELECT amount FROM transactions WHERE id = 'tx-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(500L, cursor.getLong(0))
    }
    db.query("SELECT last_sync_id FROM sync_meta WHERE owner_id = 'user-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertNull(cursor.getString(0))
    }
    db.query("SELECT COUNT(*) FROM outbox WHERE owner_id = 'user-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(1, cursor.getInt(0))
    }
    assertMatchesExportedSchema(db, 3)
    db.close()
  }

  @Test
  fun `migrate 2 to 3 preserves profiles and adds updated_at`() {
    val db = openVersion(2)
    db.execSQL(
      "INSERT INTO profiles (owner_id, name, updated_at) VALUES ('guest', 'Guest', '2026-08-01T10:00:00Z')",
    )
    db.execSQL(
      "INSERT INTO currencies (id, owner_id, code, symbol, name, is_default, created_at) " +
        "VALUES ('cur-1', 'guest', 'USD', '$', 'US Dollar', 1, '2026-08-01T10:00:00Z')",
    )

    Migrations.MIGRATION_2_3.migrate(db)

    db.query("SELECT name, updated_at FROM profiles WHERE owner_id = 'guest'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("Guest", cursor.getString(0))
      assertEquals("2026-08-01T10:00:00Z", cursor.getString(1))
    }
    db.query("SELECT updated_at FROM currencies WHERE id = 'cur-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("2026-08-01T10:00:00Z", cursor.getString(0))
    }
    assertMatchesExportedSchema(db, 3)
    db.close()
  }

  @Test
  fun `migrate 3 to 4 adds profile created_at and backfills it`() {
    val db = openVersion(3)
    db.execSQL(
      "INSERT INTO profiles (owner_id, name, updated_at) VALUES ('guest', 'Guest', '2026-08-01T10:00:00Z')",
    )

    Migrations.MIGRATION_3_4.migrate(db)

    db.query("SELECT created_at FROM profiles WHERE owner_id = 'guest'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("2026-08-01T10:00:00Z", cursor.getString(0))
    }
    assertMatchesExportedSchema(db, 4)
    db.close()
  }

  @Test
  fun `fresh install DDL matches the exported schema`() {
    val db = openVersion(4)
    assertMatchesExportedSchema(db, 4)
    db.close()
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Opens an in-memory DB and creates every table/index from the exported schema JSON. */
  private fun openVersion(version: Int): SupportSQLiteDatabase {
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(null) // in-memory
          .callback(
            object : SupportSQLiteOpenHelper.Callback(1) {
              override fun onCreate(db: SupportSQLiteDatabase) = Unit
              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
          )
          .build(),
      )
    val db = helper.writableDatabase
    // Fixtures and migrations insert data with cross-table references, so FK
    // checks are off for the test's lifetime (the migrations themselves don't
    // depend on them).
    db.execSQL("PRAGMA foreign_keys = OFF")
    val schema = loadSchema(version)
    for (entity in schema["database"]!!.jsonObject["entities"]!!.jsonArray) {
      val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
      db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
    }
    for (entity in schema["database"]!!.jsonObject["entities"]!!.jsonArray) {
      val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
      for (index in entity.jsonObject["indices"]?.jsonArray.orEmpty()) {
        db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
      }
    }
    return db
  }

  /** Approximates Room's MigrationTestHelper validation: columns, types, NOT NULL, PKs. */
  private fun assertMatchesExportedSchema(db: SupportSQLiteDatabase, version: Int) {
    val schema = loadSchema(version)
    for (entity in schema["database"]!!.jsonObject["entities"]!!.jsonArray) {
      val table = entity.jsonObject
      val tableName = table["tableName"]!!.jsonPrimitive.content
      val primaryKeyColumns = table["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
      val expected = table["fields"]!!.jsonArray.map { field ->
        val f = field.jsonObject
        ColumnSpec(
          name = f["columnName"]!!.jsonPrimitive.content,
          affinity = f["affinity"]!!.jsonPrimitive.content,
          notNull = f["notNull"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false,
          isPrimaryKey = f["columnName"]!!.jsonPrimitive.content in primaryKeyColumns,
        )
      }

      val actual = mutableMapOf<String, ColumnSpec>()
      db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIdx = cursor.getColumnIndexOrThrow("name")
        val typeIdx = cursor.getColumnIndexOrThrow("type")
        val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
        val pkIdx = cursor.getColumnIndexOrThrow("pk")
        while (cursor.moveToNext()) {
          actual[cursor.getString(nameIdx)] =
            ColumnSpec(
              name = cursor.getString(nameIdx),
              affinity = cursor.getString(typeIdx).uppercase(),
              notNull = cursor.getInt(notNullIdx) == 1,
              isPrimaryKey = cursor.getInt(pkIdx) > 0,
            )
        }
      }

      assertEquals("column count for $tableName", expected.size, actual.size)
      for (column in expected) {
        val found = actual[column.name]
        assertTrue("missing column ${tableName}.${column.name}", found != null)
        assertEquals("type for ${tableName}.${column.name}", column.affinity, found!!.affinity)
        assertEquals("notNull for ${tableName}.${column.name}", column.notNull, found.notNull)
        assertEquals("pk for ${tableName}.${column.name}", column.isPrimaryKey, found.isPrimaryKey)
      }
    }
  }

  private fun loadSchema(version: Int): JsonObject {
    // Gradle runs unit tests with the working directory set to the module dir
    // (app/), where KSP exports schemas/.
    val file =
      File(
        System.getProperty("user.dir"),
        "schemas/${AppDatabase::class.java.canonicalName}/$version.json",
      )
    assertTrue("Missing exported schema: ${file.absolutePath} (run kspDebugKotlin)", file.exists())
    return json.parseToJsonElement(file.readText()).jsonObject
  }

  private data class ColumnSpec(
    val name: String,
    val affinity: String,
    val notNull: Boolean,
    val isPrimaryKey: Boolean,
  )
}
