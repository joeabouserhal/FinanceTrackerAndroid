package com.joeabouserhal.financetracker.data.settings

enum class ThemeMode { SYSTEM, DARK, LIGHT, CUSTOM }

data class ThemeSelection(
  val mode: ThemeMode = ThemeMode.SYSTEM,
  val specId: String? = null,
)
