package com.joeabouserhal.financetracker.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryTest {
  private class Api : AuthApi {
    var requested: String? = null
    var code: String? = null
    var password: String? = null
    var failure: Exception? = null
    override suspend fun signUpWithEmail(email: String, password: String) = "active"
    override suspend fun signInWithEmail(email: String, password: String) = "active"
    override suspend fun signInWithGoogle(idToken: String) = "active"
    override suspend fun currentUserId() = "active"
    override suspend fun signOut() = Unit
    override suspend fun requestPasswordReset(email: String) { failure?.let { throw it }; requested = email }
    override suspend fun openPasswordRecovery(code: String) { this.code = code }
    override suspend fun updateRecoveredPassword(password: String) { this.password = password }
    override suspend fun clearRecovery() { code = null }
  }
  @Test fun `only exact recovery callback is accepted`() {
    assertTrue(PasswordRecovery.isCallback("$PASSWORD_RECOVERY_REDIRECT?code=abc"))
    listOf("https://auth/reset-password?code=abc", "financetracker://other/reset-password?code=abc",
      "financetracker://auth/other?code=abc", "financetracker://attacker@auth/reset-password?code=abc").forEach {
      assertFalse(it, PasswordRecovery.isCallback(it))
    }
  }
  @Test fun `callback rejects expired implicit missing and duplicate codes`() {
    listOf("", "?code=", "?code=a&code=b", "?error=expired&code=a", "#access_token=abc").forEach {
      assertThrows(IllegalArgumentException::class.java) { PasswordRecovery.code(PASSWORD_RECOVERY_REDIRECT + it) }
    }
    assertEquals("a-b", PasswordRecovery.code("$PASSWORD_RECOVERY_REDIRECT?code=a-b"))
  }
  @Test fun `request validates email trims and does not switch active user`() = runTest {
    val api = Api(); val repo = AuthRepository(api)
    assertTrue(repo.requestPasswordReset("not-email") is AuthActionResult.Error)
    assertNull(api.requested)
    assertEquals(AuthActionResult.Success, repo.requestPasswordReset(" person@example.com "))
    assertEquals("person@example.com", api.requested)
    assertEquals("active", repo.currentUserId())
  }
  @Test fun `password validates before calling server`() = runTest {
    val api = Api(); val repo = AuthRepository(api)
    assertTrue(repo.updateRecoveredPassword("123", "123") is AuthActionResult.Error)
    assertTrue(repo.updateRecoveredPassword("longpassword", "other") is AuthActionResult.Error)
    assertNull(api.password)
    assertEquals(AuthActionResult.Success, repo.updateRecoveredPassword("longpassword", "longpassword"))
    assertEquals("longpassword", api.password)
  }
  @Test fun `missing configuration and network failures are recoverable`() = runTest {
    assertTrue(AuthRepository(null).requestPasswordReset("a@example.com") is AuthActionResult.Error)
    val api = Api().apply { failure = IllegalStateException("Unable to resolve host") }
    assertTrue((AuthRepository(api).requestPasswordReset("a@example.com") as AuthActionResult.Error).message.contains("Network problem"))
  }
  @Test fun `recovery code forwarded only after validation and cancellation preserved`() = runTest {
    val api = Api(); val repo = AuthRepository(api)
    assertTrue(repo.openPasswordRecovery("https://bad/?code=abc") is AuthActionResult.Error)
    assertNull(api.code)
    assertEquals(AuthActionResult.Success, repo.openPasswordRecovery("$PASSWORD_RECOVERY_REDIRECT?code=abc"))
    assertEquals("abc", api.code)
    repo.clearRecovery(); assertNull(api.code)
    api.failure = kotlinx.coroutines.CancellationException()
    try { repo.requestPasswordReset("a@example.com"); fail("Cancellation must propagate") }
    catch (_: kotlinx.coroutines.CancellationException) { }
  }
}
