package com.joeabouserhal.financetracker.data.sync

import android.content.Context
import java.util.UUID

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
      return "%019d-%06d-%s".format(millis, fallbackCounter, fallbackDeviceId)
    }
    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val deviceId = prefs.getString(DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
      prefs.edit().putString(DEVICE_ID, it).apply()
    }
    val wall = System.currentTimeMillis()
    val previousMillis = prefs.getLong(LAST_MILLIS, 0L)
    val millis = maxOf(wall, previousMillis)
    val counter = if (millis == previousMillis) prefs.getInt(LAST_COUNTER, 0) + 1 else 0
    prefs.edit().putLong(LAST_MILLIS, millis).putInt(LAST_COUNTER, counter).apply()
    return "%019d-%06d-%s".format(millis, counter, deviceId)
  }
}
