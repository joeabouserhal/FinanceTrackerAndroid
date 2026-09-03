package com.joeabouserhal.financetracker.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.joeabouserhal.financetracker.utils.Money

/** Remove label tracking from currency symbols without changing numeric typography. */
fun compactCurrencyText(minor: Long, symbol: String, prefix: String = ""): AnnotatedString =
  buildAnnotatedString {
    append(prefix)
    append(Money.format(minor, symbol))
    if (symbol.isNotEmpty()) {
      val symbolStart = prefix.length + if (minor < 0) 1 else 0
      addStyle(SpanStyle(letterSpacing = 0.sp), symbolStart, symbolStart + symbol.length)
    }
  }
