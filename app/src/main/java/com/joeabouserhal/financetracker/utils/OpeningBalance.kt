package com.joeabouserhal.financetracker.utils

import java.math.BigDecimal
import java.math.RoundingMode

object OpeningBalance {
  /** Empty is zero; money remains exact integer minor units, including overdrafts. */
  fun parse(text: String): Long = if (text.isBlank()) 0L else try {
    BigDecimal(Money.normalizeDecimalInput(text)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
  } catch (_: NumberFormatException) {
    throw IllegalArgumentException("Enter a valid opening balance")
  } catch (_: ArithmeticException) {
    throw IllegalArgumentException("Opening balance is too large")
  }
}
