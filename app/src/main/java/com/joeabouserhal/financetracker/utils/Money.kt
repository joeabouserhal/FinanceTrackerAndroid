package com.joeabouserhal.financetracker.utils

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

object Money {
  private val formatter = NumberFormat.getIntegerInstance(Locale.US)

  /**
   * Normalizes user-typed amounts for BigDecimal parsing: "12,50" becomes
   * "12.50". (Grouped US-style input like "1,234.56" is not handled — it
   * will fail validation, which is acceptable and surfaced as an error.)
   */
  fun normalizeDecimalInput(raw: String): String = raw.trim().replace(',', '.')

  /** Format minor units (cents) with the currency symbol, e.g. 123456 -> "$1,234.56". */
  fun format(minor: Long, symbol: String, forceDecimals: Boolean = false): String {
    val sign = if (minor < 0) "-" else ""
    val abs = kotlin.math.abs(minor)
    val whole = abs / 100
    val fraction = (abs % 100).toInt()
    val grouped = formatter.format(whole)
    val decimals = if (fraction == 0 && !forceDecimals) "" else ".${fraction.toString().padStart(2, '0')}"
    return "$sign$symbol$grouped$decimals"
  }
}

object Dates {
  fun todayIso(): String = LocalDate.now().toString()

  fun monthBounds(yearMonth: YearMonth): Pair<String, String> =
    yearMonth.atDay(1).toString() to yearMonth.atEndOfMonth().toString()

  fun monthLabel(yearMonth: YearMonth): String =
    yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + yearMonth.year

  /** "2026-08-23T10:15:00.123+00:00" → "Aug 2026" in the device time zone. */
  fun formatMonthYearLabel(isoInstant: String): String =
    try {
      java.time.OffsetDateTime.parse(isoInstant)
        .atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
    } catch (_: Exception) {
      try {
        java.time.LocalDate.parse(isoInstant)
          .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
      } catch (_: Exception) {
        isoInstant
      }
    }

  /** "2026-08-23T10:15:00.123+00:00" → "Aug 23 2026, 10:15" in the device time zone. */
  fun formatInstantLabel(isoInstant: String): String =
    try {
      java.time.OffsetDateTime.parse(isoInstant)
        .atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm"))
    } catch (_: Exception) {
      try {
        java.time.LocalDateTime.parse(isoInstant)
          .format(java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm"))
      } catch (_: Exception) {
        isoInstant
      }
    }
}
