package com.joeabouserhal.financetracker.data.sync

import com.joeabouserhal.financetracker.data.local.entities.AccountEntity
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.GoalEntity
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.entities.ProfileEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Maps server rows (snake_case JSON from PostgREST) to local Room entities.
 * Throws on malformed rows; the engine skips bad rows and retries them on a
 * later sync.
 */
object SyncMappers {

  fun currency(row: JsonObject): CurrencyEntity =
    CurrencyEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      code = row.reqString("code"),
      symbol = row.reqString("symbol"),
      name = row.reqString("name"),
      isDefault = row.optBool("is_default"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun category(row: JsonObject): CategoryEntity =
    CategoryEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      name = row.reqString("name"),
      type = row.type(),
      color = row.reqString("color"),
      isDefault = row.optBool("is_default"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun account(row: JsonObject): AccountEntity =
    AccountEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      currencyId = row.reqString("currency_id"),
      name = row.reqString("name"),
      archived = row.optBool("archived"),
      isDefault = row.optBool("is_default"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun preset(row: JsonObject): PresetEntity =
    PresetEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      name = row.reqString("name"),
      type = row.type(),
      defaultAmount = row.optLong("default_amount"),
      defaultCurrencyId = row.optString("default_currency_id"),
      defaultCategoryId = row.optString("default_category_id"),
      defaultAccountId = row.optString("default_account_id"),
      archived = row.optBool("archived"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun goal(row: JsonObject): GoalEntity =
    GoalEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      name = row.reqString("name"),
      targetMinor = row.reqLong("target_minor"),
      currencyId = row.reqString("currency_id"),
      accountId = row.optString("account_id"),
      completed = row.optBool("completed"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun transaction(row: JsonObject): TransactionEntity =
    TransactionEntity(
      id = row.reqString("id"),
      ownerId = row.reqString("user_id"),
      type = row.type(),
      amount = row.reqLong("amount"),
      currencyId = row.reqString("currency_id"),
      categoryId = row.reqString("category_id"),
      accountId = row.optString("account_id"),
      date = row.reqString("date"),
      title = row.optString("title"),
      notes = row.optString("notes"),
      presetId = row.optString("preset_id"),
      goalId = row.optString("goal_id"),
      createdAt = row.createdAt(),
      updatedAt = row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
      deletedAt = row.optString("deleted_at"),
    )

  fun profile(row: JsonObject): ProfileEntity =
    ProfileEntity(
      ownerId = row.reqString("user_id"),
      name = row.reqString("name"),
      updatedAt = row.reqString("updated_at"),
      createdAt = row.optString("created_at")?.takeIf { it.isNotBlank() } ?: row.reqString("updated_at"),
      syncVersion = row.optString("sync_version") ?: row.reqString("updated_at"),
    )

  /** Remote wins on the hybrid logical clock; legacy ISO values remain comparable during rollout. */
  fun remoteAtLeastAsNew(localUpdatedAt: String, remoteUpdatedAt: String): Boolean =
    when {
      localUpdatedAt.isHlc() && remoteUpdatedAt.isHlc() -> localUpdatedAt <= remoteUpdatedAt
      else -> localUpdatedAt.toEpochMillis() <= remoteUpdatedAt.toEpochMillis()
    }

  private fun String.isHlc(): Boolean = length > 20 && this[19] == '-'

  private fun String.toEpochMillis(): Long =
    if (isHlc()) substring(0, 19).toLongOrNull() ?: Long.MIN_VALUE
    else try { Instant.parse(this).toEpochMilli() } catch (_: Exception) { Long.MIN_VALUE }

  private fun JsonObject.type(): TransactionType =
    TransactionType.valueOf(reqString("type").uppercase())

  private fun JsonObject.createdAt(): String = optString("created_at") ?: reqString("updated_at")

  private fun JsonObject.reqString(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("Missing field '$key'")

  private fun JsonObject.optString(key: String): String? =
    when (val v = this[key]) {
      null, JsonNull -> null
      is JsonPrimitive -> v.contentOrNull
      else -> throw IllegalArgumentException("Field '$key' is not a string")
    }

  private fun JsonObject.optBool(key: String, default: Boolean = false): Boolean =
    when (val v = this[key]) {
      null, JsonNull -> default
      is JsonPrimitive -> v.contentOrNull?.toBoolean() ?: default
      else -> default
    }

  private fun JsonObject.optLong(key: String): Long? =
    when (val v = this[key]) {
      null, JsonNull -> null
      is JsonPrimitive -> v.contentOrNull?.toLongOrNull()
      else -> throw IllegalArgumentException("Field '$key' is not a number")
    }

  private fun JsonObject.reqLong(key: String): Long =
    optLong(key) ?: throw IllegalArgumentException("Missing field '$key'")
}
