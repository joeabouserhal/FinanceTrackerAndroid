package com.joeabouserhal.financetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joeabouserhal.financetracker.data.local.dao.AccountDao
import com.joeabouserhal.financetracker.data.local.dao.CategoryDao
import com.joeabouserhal.financetracker.data.local.dao.CurrencyDao
import com.joeabouserhal.financetracker.data.local.dao.GoalDao
import com.joeabouserhal.financetracker.data.local.dao.OutboxDao
import com.joeabouserhal.financetracker.data.local.dao.PresetDao
import com.joeabouserhal.financetracker.data.local.dao.ProfileDao
import com.joeabouserhal.financetracker.data.local.dao.RecurringOccurrenceDao
import com.joeabouserhal.financetracker.data.local.dao.RecurringScheduleDao
import com.joeabouserhal.financetracker.data.local.dao.SyncMetaDao
import com.joeabouserhal.financetracker.data.local.dao.TransactionDao
import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.local.entities.OutboxEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import com.joeabouserhal.financetracker.data.local.entities.SyncMetaEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.RecurringOccurrenceEntity
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity

@Database(
  entities =
    [
      CurrencyEntity::class,
      AccountEntity::class,
      GoalEntity::class,
      CategoryEntity::class,
      PresetEntity::class,
      TransactionEntity::class,
      ProfileEntity::class,
      OutboxEntity::class,
      SyncMetaEntity::class,
      com.joeabouserhal.financetracker.data.local.entities.SyncHealthEntity::class,
      RecurringScheduleEntity::class,
      RecurringOccurrenceEntity::class,
      com.joeabouserhal.financetracker.data.local.entities.RecurringProgressEntity::class,
    ],
  version = 17,
  exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun currencyDao(): CurrencyDao
  abstract fun accountDao(): AccountDao
  abstract fun goalDao(): GoalDao
  abstract fun categoryDao(): CategoryDao
  abstract fun presetDao(): PresetDao
  abstract fun transactionDao(): TransactionDao
  abstract fun profileDao(): ProfileDao
  abstract fun outboxDao(): OutboxDao
  abstract fun syncMetaDao(): SyncMetaDao
  abstract fun syncHealthDao(): com.joeabouserhal.financetracker.data.local.dao.SyncHealthDao
  abstract fun recurringScheduleDao(): RecurringScheduleDao
  abstract fun recurringOccurrenceDao(): RecurringOccurrenceDao
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

  val MIGRATION_5_6 =
    object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `goals` (
            `id` TEXT NOT NULL,
            `owner_id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `target_minor` INTEGER NOT NULL,
            `currency_id` TEXT NOT NULL,
            `account_id` TEXT,
            `created_at` TEXT NOT NULL,
            `updated_at` TEXT NOT NULL DEFAULT '',
            PRIMARY KEY(`id`),
            FOREIGN KEY(`currency_id`) REFERENCES `currencies`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_owner_id` ON `goals` (`owner_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_currency_id` ON `goals` (`currency_id`)")
      }
    }

  val MIGRATION_6_7 =
    object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `goals` ADD COLUMN `completed` INTEGER NOT NULL DEFAULT 0")
      }
    }

  val MIGRATION_7_8 =
    object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `goal_id` TEXT DEFAULT NULL REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_goal_id` ON `transactions` (`goal_id`)")
      }
    }

  /** Sync protocol metadata. Tombstones are hidden by DAO queries, never hard-deleted. */
  val MIGRATION_8_9 =
    object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        val tombstoned = arrayOf("currencies", "categories", "accounts", "presets", "goals", "transactions")
        tombstoned.forEach { table ->
          db.execSQL("ALTER TABLE `$table` ADD COLUMN `sync_version` TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE `$table` ADD COLUMN `deleted_at` TEXT DEFAULT NULL")
          db.execSQL("UPDATE `$table` SET `sync_version` = `updated_at` WHERE `sync_version` = ''")
        }
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `sync_version` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `profiles` SET `sync_version` = `updated_at` WHERE `sync_version` = ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_account_id` ON `goals` (`account_id`)")
      }
    }

  /** Stable operation ids let a cancelled worker retry a push without replaying it remotely. */
  val MIGRATION_9_10 =
    object : Migration(9, 10) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `op_id` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `outbox` SET `op_id` = 'legacy-' || `id` WHERE `op_id` = ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_op_id` ON `outbox` (`op_id`)")
      }
    }

  val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) = SyncRecoveryMigration.migrate(db)
  }

  // Version 12 briefly held local assistant confirmations. Keep this bridge so
  // installed preview builds can upgrade safely, then remove the unused data.
  val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `assistant_commands` (
          `id` TEXT NOT NULL,
          `owner_id` TEXT NOT NULL,
          `action` TEXT NOT NULL,
          `payload_json` TEXT NOT NULL,
          `created_at` INTEGER NOT NULL,
          `expires_at` INTEGER NOT NULL,
          `consumed_at` INTEGER,
          PRIMARY KEY(`id`)
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_commands_owner_id` ON `assistant_commands` (`owner_id`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_commands_expires_at` ON `assistant_commands` (`expires_at`)")
    }
  }

  /** Removes temporary assistant storage without affecting finance records. */
  val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("DROP TABLE IF EXISTS `assistant_commands`")
    }
  }

  val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurrence_occurrence_id` TEXT DEFAULT NULL")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurrence_occurrence_id` ON `transactions` (`recurrence_occurrence_id`)")
      db.execSQL("""
        CREATE TABLE IF NOT EXISTS `recurring_schedules` (
          `id` TEXT NOT NULL, `owner_id` TEXT NOT NULL, `preset_id` TEXT NOT NULL,
          `frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `week_days` TEXT,
          `monthly_rule` TEXT NOT NULL, `day_of_month` INTEGER, `nth_weekday` INTEGER,
          `nth_ordinal` INTEGER, `start_date` TEXT NOT NULL, `end_date` TEXT,
          `time_minutes` INTEGER NOT NULL, `paused` INTEGER NOT NULL,
          `blocked_reason` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
          `sync_version` TEXT NOT NULL, `deleted_at` TEXT,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`preset_id`) REFERENCES `presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
      """.trimIndent())
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedules_owner_id` ON `recurring_schedules` (`owner_id`)")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_schedules_owner_id_preset_id` ON `recurring_schedules` (`owner_id`, `preset_id`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_schedules_preset_id` ON `recurring_schedules` (`preset_id`)")
      db.execSQL("""
        CREATE TABLE IF NOT EXISTS `recurring_occurrences` (
          `id` TEXT NOT NULL, `owner_id` TEXT NOT NULL, `schedule_id` TEXT NOT NULL,
          `occurrence_key` TEXT NOT NULL, `state` TEXT NOT NULL, `effective_at` INTEGER NOT NULL,
          `transaction_id` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
          `sync_version` TEXT NOT NULL, `deleted_at` TEXT,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`schedule_id`) REFERENCES `recurring_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
      """.trimIndent())
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_owner_id` ON `recurring_occurrences` (`owner_id`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_schedule_id` ON `recurring_occurrences` (`schedule_id`)")
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_owner_id_schedule_id_occurrence_key` ON `recurring_occurrences` (`owner_id`, `schedule_id`, `occurrence_key`)")
    }
  }

  val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("""
        CREATE TABLE `transactions_new` (
          `id` TEXT NOT NULL, `owner_id` TEXT NOT NULL, `type` TEXT NOT NULL,
          `amount` INTEGER NOT NULL, `currency_id` TEXT NOT NULL, `category_id` TEXT,
          `account_id` TEXT, `date` TEXT NOT NULL, `title` TEXT, `notes` TEXT,
          `preset_id` TEXT, `goal_id` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
          `sync_version` TEXT NOT NULL DEFAULT '', `deleted_at` TEXT,
          `recurrence_occurrence_id` TEXT, `destination_account_id` TEXT,
          PRIMARY KEY(`id`),
          FOREIGN KEY(`currency_id`) REFERENCES `currencies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
          FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
          FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
          FOREIGN KEY(`destination_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
          FOREIGN KEY(`preset_id`) REFERENCES `presets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
          FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
      """.trimIndent())
      db.execSQL("""
        INSERT INTO `transactions_new` (id,owner_id,type,amount,currency_id,category_id,account_id,date,title,notes,
          preset_id,goal_id,created_at,updated_at,sync_version,deleted_at,recurrence_occurrence_id)
        SELECT id,owner_id,type,amount,currency_id,category_id,account_id,date,title,notes,
          preset_id,goal_id,created_at,updated_at,sync_version,deleted_at,recurrence_occurrence_id FROM `transactions`
      """.trimIndent())
      db.execSQL("DROP TABLE `transactions`")
      db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
      listOf("owner_id", "currency_id", "category_id", "account_id", "destination_account_id", "preset_id", "goal_id", "recurrence_occurrence_id").forEach {
        db.execSQL("CREATE INDEX `index_transactions_${it}` ON `transactions` (`${it}`)")
      }
      db.execSQL("CREATE INDEX `index_transactions_owner_id_date` ON `transactions` (`owner_id`, `date`)")
    }
  }

  val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `outbox` ADD COLUMN `batch_id` TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE `outbox` ADD COLUMN `batch_index` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `outbox` ADD COLUMN `state` TEXT NOT NULL DEFAULT 'PENDING'")
      db.execSQL("ALTER TABLE `outbox` ADD COLUMN `error_code` TEXT")
      db.execSQL("ALTER TABLE `outbox` ADD COLUMN `next_attempt_at` TEXT")
      // Old queues retain their stable operation IDs and original order.
      db.execSQL("UPDATE `outbox` SET `batch_id` = `op_id`, `state` = CASE WHEN error_kind IN ('ACTION_REQUIRED','AUTH_REQUIRED') THEN 'ACTION_REQUIRED' WHEN attempts > 0 THEN 'RETRYING' ELSE 'PENDING' END")
      db.execSQL("ALTER TABLE `recurring_occurrences` ADD COLUMN `generation` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurrence_generation` INTEGER NOT NULL DEFAULT 0")
      db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_progress` (`owner_id` TEXT NOT NULL, `schedule_id` TEXT NOT NULL, `through_date` TEXT NOT NULL, PRIMARY KEY(`owner_id`,`schedule_id`))")
      db.execSQL("ALTER TABLE `sync_health` ADD COLUMN `last_push_at` TEXT")
      db.execSQL("ALTER TABLE `sync_health` ADD COLUMN `last_pull_at` TEXT")
      db.execSQL("ALTER TABLE `sync_health` ADD COLUMN `conflicts_resolved` INTEGER NOT NULL DEFAULT 0")
    }
  }

  val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `accounts` ADD COLUMN `opening_balance` INTEGER NOT NULL DEFAULT 0")
    }
  }

  val ALL: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
}
