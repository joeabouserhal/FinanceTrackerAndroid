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
}

class SupabaseAuthApi(private val client: SupabaseClient) : AuthApi {
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
    client.auth.signOut()
  }

  private suspend fun currentUser(): String? = client.auth.currentUserOrNull()?.id
}
