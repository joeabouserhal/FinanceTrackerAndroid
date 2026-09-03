package com.joeabouserhal.financetracker.data.sync

import android.content.Context
import java.util.UUID
import java.util.Locale

/**
 * A small, durable hybrid logical clock used to order edits made while a
 * device is offline.  The fixed-width representation sorts lexicographically
 * in the same order as the logical clock, which makes it safe to compare in
 * Postgres as text.
 */
object SyncVersion {
  private const val PREFS = "sync_clock"
  private const val DEVICE_ID = "device_id"
  private const val LAST_MILLIS = "last_millis"
  private const val LAST_COUNTER = "last_counter"

  @Volatile private var context: Context? = null
  private val fallbackDeviceId = "test-" + UUID.randomUUID().toString()
  private var fallbackMillis = 0L
  private var fallbackCounter = 0

  fun initialize(appContext: Context) {
    context = appContext.applicationContext
  }

  @Synchronized
  fun next(): String {
    val appContext = context
    if (appContext == null) {
      val millis = maxOf(System.currentTimeMillis(), fallbackMillis)
      fallbackCounter = if (millis == fallbackMillis) fallbackCounter + 1 else 0
      fallbackMillis = millis
      return String.format(Locale.ROOT, "%019d-%06d-%s", millis, fallbackCounter, fallbackDeviceId)
    }
    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val deviceId = prefs.getString(DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
      check(prefs.edit().putString(DEVICE_ID, it).commit()) { "Could not persist sync device identity" }
    }
    val wall = System.currentTimeMillis()
    val previousMillis = prefs.getLong(LAST_MILLIS, 0L)
    val millis = maxOf(wall, previousMillis)
    val counter = if (millis == previousMillis) prefs.getInt(LAST_COUNTER, 0) + 1 else 0
    check(prefs.edit().putLong(LAST_MILLIS, millis).putInt(LAST_COUNTER, counter).commit()) { "Could not persist sync clock" }
    return String.format(Locale.ROOT, "%019d-%06d-%s", millis, counter, deviceId)
  }

  /** A local edit after a remote edit must order after it, even with clock skew. */
  @Synchronized
  fun observe(version: String) {
    if (version.length < 27 || version[19] != '-') return
    val millis = version.substring(0, 19).toLongOrNull() ?: return
    val counter = version.substring(20, 26).toIntOrNull() ?: return
    val appContext = context
    if (appContext == null) {
      if (millis > fallbackMillis || (millis == fallbackMillis && counter > fallbackCounter)) {
        fallbackMillis = millis
        fallbackCounter = counter
      }
      return
    }
    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val previousMillis = prefs.getLong(LAST_MILLIS, 0L)
    if (millis > previousMillis || (millis == previousMillis && counter > prefs.getInt(LAST_COUNTER, 0))) {
      check(prefs.edit().putLong(LAST_MILLIS, millis).putInt(LAST_COUNTER, counter).commit()) { "Could not persist remote sync clock" }
    }
  }
}
