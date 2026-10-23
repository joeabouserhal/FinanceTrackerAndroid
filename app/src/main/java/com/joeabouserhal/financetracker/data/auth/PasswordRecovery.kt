package com.joeabouserhal.financetracker.data.auth

import java.net.URI
import java.net.URLDecoder

const val PASSWORD_RECOVERY_REDIRECT = "financetracker://auth/reset-password"

object PasswordRecovery {
  fun isCallback(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    uri.scheme == "financetracker" && uri.host == "auth" && uri.path == "/reset-password" && uri.userInfo == null
  }.getOrDefault(false)

  fun code(raw: String): String {
    require(isCallback(raw)) { "Invalid password reset link." }
    val query = URI(raw).rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.map {
      val pair = it.split('=', limit = 2)
      URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }
    require(query.none { it.first == "error" || it.first == "error_code" }) { "This reset link has expired. Request a new one." }
    return query.filter { it.first == "code" }.singleOrNull()?.second?.takeIf { it.isNotBlank() && it.length <= 2048 }
      ?: throw IllegalArgumentException("Invalid or expired reset link. Request a new one from this phone.")
  }

  fun validatePassword(password: String, confirmation: String): String? = when {
    password.length < 6 -> "Use at least 6 characters."
    password != confirmation -> "Passwords do not match."
    else -> null
  }
}

sealed interface AuthActionResult {
  data object Success : AuthActionResult
  data class Error(val message: String) : AuthActionResult
}
