package com.joeabouserhal.financetracker.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken

/** Minimal auth surface the app needs — faked in unit tests. */
interface AuthApi {
  /** Returns the new user's id. */
  suspend fun signUpWithEmail(email: String, password: String): String

  /** Returns the signed-in user's id. */
  suspend fun signInWithEmail(email: String, password: String): String

  /** Returns the signed-in user's id. */
  suspend fun signInWithGoogle(idToken: String): String

  /** Id of the currently signed-in user, or null. */
  suspend fun currentUserId(): String?

  suspend fun signOut()
  suspend fun requestPasswordReset(email: String)
  suspend fun openPasswordRecovery(code: String)
  suspend fun updateRecoveredPassword(password: String)
  suspend fun clearRecovery()
}

class SupabaseAuthApi(private val client: SupabaseClient, private val recoveryClient: SupabaseClient) : AuthApi {
  private var openedRecoveryCode: String? = null

  override suspend fun requestPasswordReset(email: String) {
    recoveryClient.auth.resetPasswordForEmail(email, redirectUrl = PASSWORD_RECOVERY_REDIRECT)
  }

  override suspend fun openPasswordRecovery(code: String) {
    if (openedRecoveryCode == code && recoveryClient.auth.currentSessionOrNull() != null) return
    // Do not clearSession here: it also deletes the PKCE verifier needed for this exchange.
    openedRecoveryCode = null
    recoveryClient.auth.exchangeCodeForSession(code)
    openedRecoveryCode = code
  }

  override suspend fun updateRecoveredPassword(password: String) {
    check(openedRecoveryCode != null && recoveryClient.auth.currentSessionOrNull() != null) { "Reset link expired. Request a new one." }
    recoveryClient.auth.updateUser { this.password = password }
    clearRecovery()
  }

  override suspend fun clearRecovery() {
    openedRecoveryCode = null
    recoveryClient.auth.clearSession()
  }
  override suspend fun signUpWithEmail(email: String, password: String): String =
    client.auth.signUpWith(Email) {
      this.email = email
      this.password = password
    }?.id ?: throw IllegalStateException("Sign-up succeeded but no user was returned")

  override suspend fun signInWithEmail(email: String, password: String): String {
    client.auth.signInWith(Email) {
      this.email = email
      this.password = password
    }
    return currentUser() ?: throw IllegalStateException("Sign-in succeeded but no user was returned")
  }

  override suspend fun signInWithGoogle(idToken: String): String {
    client.auth.signInWith(IDToken) {
      this.idToken = idToken
      provider = Google
    }
    return currentUser() ?: throw IllegalStateException("Google sign-in succeeded but no user was returned")
  }

  override suspend fun currentUserId(): String? = currentUser()

  override suspend fun signOut() {
    try {
      kotlinx.coroutines.withTimeoutOrNull(3_000) { client.auth.signOut() }
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (_: Exception) {
      // Revoking remotely is best-effort offline; never retain local credentials.
    } finally {
      kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { client.auth.clearSession() }
    }
  }

  private suspend fun currentUser(): String? = client.auth.currentUserOrNull()?.id
}
