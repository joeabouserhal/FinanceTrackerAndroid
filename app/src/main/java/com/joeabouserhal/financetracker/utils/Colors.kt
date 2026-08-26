package com.joeabouserhal.financetracker.utils

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for parsing category/UI colors.
 * Accepts "#RRGGBB" (tolerates a missing "#" — some persisted histories
 * store hex without it) and falls back to a neutral gray on garbage input.
 */
fun parseHexColor(hex: String): Color {
  val normalized = if (hex.startsWith("#")) hex else "#$hex"
  return try {
    Color(android.graphics.Color.parseColor(normalized))
  } catch (_: IllegalArgumentException) {
    Color(0xFF77746C)
  }
}
