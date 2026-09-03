package com.joeabouserhal.financetracker.data.sync

import io.github.jan.supabase.postgrest.exception.PostgrestRestException

data class SyncFailure(val kind: String, val message: String) {
  companion object {
    fun from(error: Exception): SyncFailure {
      val code = (error as? PostgrestRestException)?.code
      return when {
        code in setOf("42501", "28000", "PGRST301", "PGRST302", "PGRST303") ->
          SyncFailure("AUTH_REQUIRED", "Sign in again to resume sync ($code).")
        code == "23503" -> SyncFailure("RETRYING", "A related record has not synced yet ($code). Retrying safely.")
        code != null && (code.startsWith("22") || code in setOf("23502", "23505", "23514")) ->
          SyncFailure("ACTION_REQUIRED", "The server rejected this record ($code). Check its fields and references.")
        error is IllegalArgumentException ->
          SyncFailure("ACTION_REQUIRED", "Sync data could not be validated. Local data has been kept.")
        else -> SyncFailure("RETRYING", "Sync was interrupted (${code ?: error.javaClass.simpleName}). Retrying automatically.")
      }
    }
  }
}
