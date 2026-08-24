package com.joeabouserhal.financetracker.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.joeabouserhal.financetracker.data.remote.SupabaseConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleSignInResult {
  data class Success(val idToken: String) : GoogleSignInResult
  data class Failure(val message: String) : GoogleSignInResult
}

/**
 * Credential Manager → Google ID token. The token is then exchanged with
 * Supabase for a session via AuthApi.signInWithGoogle.
 *
 * Console setup (documented in supabase/README.md):
 *  - Supabase Auth → Providers → Google: enable + add the Google Cloud
 *    OAuth client ID/secret.
 *  - Google Cloud: OAuth Client ID → Android with this app's package name and
 *    the debug keystore SHA-1.
 *  - Put the web client ID in gradle.properties as googleServerClientId.
 */
class GoogleSignIn(context: Context) {
  private val appContext = context.applicationContext

  fun isConfigured(): Boolean = SupabaseConfig.isGoogleConfigured

  suspend fun getIdToken(): GoogleSignInResult {
    if (!SupabaseConfig.isGoogleConfigured) {
      return GoogleSignInResult.Failure("Google sign-in is not configured — check supabase/README.md")
    }
    val credentialManager = CredentialManager.create(appContext)
    val request =
      GetCredentialRequest.Builder()
        .addCredentialOption(
          GetGoogleIdOption.Builder()
            .setServerClientId(SupabaseConfig.googleServerClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build(),
        )
        .build()

    return try {
      val response = credentialManager.getCredential(appContext, request)
      val credential = response.credential
      if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        GoogleSignInResult.Success(googleCredential.idToken)
      } else {
        GoogleSignInResult.Failure("Unexpected credential type — try again")
      }
    } catch (e: GetCredentialCancellationException) {
      GoogleSignInResult.Failure("Sign-in cancelled")
    } catch (e: GetCredentialException) {
      val detail = e.errorMessage?.toString()?.take(100) ?: ""
      GoogleSignInResult.Failure(
        when {
          e.type.contains("CANCELLATION", ignoreCase = true) || e.type.contains("CANCELED", ignoreCase = true) ->
            "Sign-in cancelled"
          e.type.contains("NO_CREDENTIAL", ignoreCase = true) ->
            "Google sign-in couldn't find a usable account. If you do have a Google account on this device, check the Google Cloud OAuth Android client: package com.joeabouserhal.financetracker plus this build's signing SHA-1 (see supabase/README.md §4)"
          e.type.contains("INTERRUPTED", ignoreCase = true) ->
            "Sign-in interrupted — try again"
          e.type.contains("CONFIGURATION", ignoreCase = true) ->
            "Google sign-in setup problem — check the Android OAuth client (package + SHA-1) and the web client id"
          else -> "Google sign-in failed (${detail.ifBlank { e.type }})"
        },
      )
    } catch (e: GoogleIdTokenParsingException) {
      GoogleSignInResult.Failure("Google sign-in failed — invalid token")
    }
  }
}
