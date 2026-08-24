package com.joeabouserhal.financetracker.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAuthApi(
  var signUpResult: String? = "user-1",
  var signInResult: String? = "user-1",
  var googleResult: String? = "user-1",
  var throwOnSignIn: Exception? = null,
) : AuthApi {
  override suspend fun signUpWithEmail(email: String, password: String): String =
    signUpResult ?: throw IllegalStateException("no user")

  override suspend fun signInWithEmail(email: String, password: String): String {
    throwOnSignIn?.let { throw it }
    return signInResult ?: throw IllegalStateException("no user")
  }

  override suspend fun signInWithGoogle(idToken: String): String =
    googleResult ?: throw IllegalStateException("no user")

  override suspend fun currentUserId(): String? = signInResult

  override suspend fun signOut() = Unit
}

class AuthRepositoryTest {
  @Test
  fun `success returns user id`() = runTest {
    val repo = AuthRepository(FakeAuthApi())
    assertEquals(AuthResult.Success("user-1"), repo.signIn("a@b.c", "password"))
    assertEquals(AuthResult.Success("user-1"), repo.signUp("a@b.c", "password"))
    assertEquals(AuthResult.Success("user-1"), repo.signInWithGoogle("token"))
  }

  @Test
  fun `missing api fails fast without network`() = runTest {
    val repo = AuthRepository(null)
    assertTrue(!repo.isAvailable())
    val result = repo.signIn("a@b.c", "password")
    assertTrue(result is AuthResult.Error)
    assertTrue((result as AuthResult.Error).message.contains("not configured"))
  }

  @Test
  fun `null user id maps to error`() = runTest {
    val repo = AuthRepository(FakeAuthApi(signInResult = null))
    assertTrue(repo.signIn("a@b.c", "password") is AuthResult.Error)
  }

  @Test
  fun `friendly error mapping`() = runTest {
    val invalid = AuthRepository(FakeAuthApi(throwOnSignIn = IllegalStateException("Invalid login credentials")))
    assertEquals(AuthResult.Error("Wrong email or password"), invalid.signIn("a@b.c", "x"))

    val registered = AuthRepository(FakeAuthApi(throwOnSignIn = IllegalStateException("User already registered")))
    assertEquals(AuthResult.Error("An account with this email already exists"), registered.signIn("a@b.c", "x"))

    val rateLimited = AuthRepository(FakeAuthApi(throwOnSignIn = IllegalStateException("Request rate limit reached")))
    assertTrue((rateLimited.signIn("a@b.c", "x") as AuthResult.Error).message.contains("Too many attempts"))

    val offline = AuthRepository(FakeAuthApi(throwOnSignIn = IllegalStateException("Unable to resolve host")))
    assertTrue((offline.signIn("a@b.c", "x") as AuthResult.Error).message.contains("Network problem"))
  }
}
