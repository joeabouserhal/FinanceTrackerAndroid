package com.joeabouserhal.financetracker.data.remote

import com.joeabouserhal.financetracker.BuildConfig

/**
 * Supabase settings, injected at build time from gradle.properties /
 * local.properties. Empty values mean "not configured" — the app then runs
 * in guest-only mode and never touches the network.
 */
object SupabaseConfig {
  val url: String = BuildConfig.SUPABASE_URL
  val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
  val googleServerClientId: String = BuildConfig.GOOGLE_SERVER_CLIENT_ID

  val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
  val isGoogleConfigured: Boolean get() = isConfigured && googleServerClientId.isNotBlank()
}
