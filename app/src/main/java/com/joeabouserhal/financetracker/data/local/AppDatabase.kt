package com.joeabouserhal.financetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joeabouserhal.financetracker.data.local.dao.AccountDao
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.CurrencyDao
import com.joeabouserhal.financetracker.data.local.dao.OutboxDao
import com.joeabouserhal.financetracker.data.local.dao.PresetDao
import com.joeabouserhal.financetracker.data.local.dao.ProfileDao
import com.joeabouserhal.financetracker.data.local.dao.SyncMetaDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import com.joeabouserhal.financetracker.data.local.entities.SyncMetaEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity

@Database(
  entities =
    [
      CurrencyEntity::class,
      AccountEntity::class,
      CategoryEntity::class,
      PresetEntity::class,
      TransactionEntity::class,
      ProfileEntity::class,
      OutboxEntity::class,
      SyncMetaEntity::class,
    ],
  version = 5,
  exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun currencyDao(): CurrencyDao
  abstract fun accountDao(): AccountDao
  abstract fun categoryDao(): CategoryDao
  abstract fun presetDao(): PresetDao
  abstract fun transactionDao(): TransactionDao
  abstract fun profileDao(): ProfileDao
  abstract fun outboxDao(): OutboxDao
  abstract fun syncMetaDao(): SyncMetaDao
}

/** Migration registry. Every schema change must add an entry here and bump [AppDatabase.version]. */
object Migrations {
  val MIGRATION_1_2 =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `profiles` (
            `owner_id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `updated_at` TEXT NOT NULL,
            PRIMARY KEY(`owner_id`)
          )
          """.trimIndent(),
        )
      }
    }

  val MIGRATION_2_3 =
    object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // LWW pull columns, backfilled from created_at for pre-existing rows.
        db.execSQL("ALTER TABLE `currencies` ADD COLUMN `updated_at` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `updated_at` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `updated_at` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `presets` ADD COLUMN `updated_at` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `currencies` SET `updated_at` = `created_at` WHERE `updated_at` = ''")
        db.execSQL("UPDATE `accounts` SET `updated_at` = `created_at` WHERE `updated_at` = ''")
        db.execSQL("UPDATE `categories` SET `updated_at` = `created_at` WHERE `updated_at` = ''")
        db.execSQL("UPDATE `presets` SET `updated_at` = `created_at` WHERE `updated_at` = ''")
        // Keyset cursor second column for the pull watermark.
        db.execSQL("ALTER TABLE `sync_meta` ADD COLUMN `last_sync_id` TEXT")
      }
    }

  val MIGRATION_3_4 =
    object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Account creation date for the profile card; backfill from the
        // last profile edit, which for most rows is the signup-pull moment.
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `created_at` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `profiles` SET `created_at` = `updated_at` WHERE `created_at` = ''")
      }
    }

  val MIGRATION_4_5 =
    object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Default account per currency: one active account per currency is
        // promoted so existing data has exactly one default.
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `is_default` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
          """
          UPDATE `accounts`
          SET `is_default` = 1
          WHERE `archived` = 0 AND `id` IN (
            SELECT MIN(`id`) FROM `accounts` WHERE `archived` = 0 GROUP BY `owner_id`, `currency_id`
          )
          """.trimIndent(),
        )
      }
    }

  val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
