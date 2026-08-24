package com.joeabouserhal.financetracker.data.auth

/** Result of an auth attempt, with user-friendly error mapping. */
sealed interface AuthResult {
  data class Success(val userId: String) : AuthResult
  data class Error(val message: String) : AuthResult
}

/**
 * Wraps [AuthApi] with friendly error mapping. [api] is null when Supabase is
 * not configured; every call then fails fast without touching the network.
 */
class AuthRepository(private val api: AuthApi?) {

  fun isAvailable(): Boolean = api != null

  suspend fun signUp(email: String, password: String): AuthResult = call { api?.signUpWithEmail(email, password) }

  suspend fun signIn(email: String, password: String): AuthResult = call { api?.signInWithEmail(email, password) }

  suspend fun signInWithGoogle(idToken: String): AuthResult = call { api?.signInWithGoogle(idToken) }

  suspend fun currentUserId(): String? = api?.currentUserId()

  suspend fun signOut() {
    api?.signOut()
  }

  private suspend fun call(block: suspend () -> String?): AuthResult {
    if (api == null) {
      return AuthResult.Error("Supabase is not configured yet — check supabase/README.md")
    }
    return try {
      val userId = block()
      if (userId.isNullOrBlank()) AuthResult.Error("Authentication failed — no user returned")
      else AuthResult.Success(userId)
    } catch (e: Exception) {
      AuthResult.Error(mapError(e))
    }
  }

  private fun mapError(e: Exception): String {
    val raw = e.message ?: ""
    return when {
      raw.contains("Invalid login credentials", ignoreCase = true) -> "Wrong email or password"
      raw.contains("User already registered", ignoreCase = true) -> "An account with this email already exists"
      raw.contains("Email not confirmed", ignoreCase = true) -> "Confirm your email first, then try again"
      raw.contains("rate limit", ignoreCase = true) || raw.contains("429") -> "Too many attempts — wait a minute and try again"
      raw.contains("password should be", ignoreCase = true) -> "Password must be at least 6 characters"
      raw.contains("Unable to resolve host", ignoreCase = true) ||
        raw.contains("Failed to connect", ignoreCase = true) ||
        raw.contains("timeout", ignoreCase = true) -> "Network problem — check your connection"
      else -> raw.ifBlank { "Something went wrong — try again" }
    }
  }
}
