package com.joeabouserhal.financetracker.data.remote

import android.content.Context
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Lazy Supabase client. Null when not configured, so the guest partition can
 * never accidentally open a network connection.
 */
class SupabaseClientProvider(context: Context) {
  private val appContext = context.applicationContext

  val client: SupabaseClient? by lazy {
    if (!SupabaseConfig.isConfigured) {
      null
    } else {
      createSupabaseClient(SupabaseConfig.url, SupabaseConfig.anonKey) {
        install(Auth) {
          // Persists the session across app restarts (SharedPreferences).
          sessionManager = SettingsSessionManager(Settings())
          alwaysAutoRefresh = true
        }
        install(Postgrest)
      }
    }
  }
}
