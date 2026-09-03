package com.joeabouserhal.financetracker.ui.components

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactCurrencyTextTest {
  @Test
  fun `only currency characters receive compact spacing`() {
    listOf("$", "LBP", "ل.ل").forEach { symbol ->
      val result = compactCurrencyText(123456, symbol, prefix = "+\u2009")
      assertEquals("+\u2009${symbol}1,234.56", result.text)
      val span = result.spanStyles.single()
      assertEquals(2, span.start)
      assertEquals(2 + symbol.length, span.end)
      assertEquals(0.sp, span.item.letterSpacing)
    }
  }

  @Test
  fun `negative balances preserve the sign outside the currency span`() {
    val result = compactCurrencyText(-250000, "LBP")
    assertEquals("-LBP2,500", result.text)
    assertEquals(1, result.spanStyles.single().start)
    assertEquals(4, result.spanStyles.single().end)
  }

  @Test
  fun `empty currency leaves number typography untouched`() {
    val result = compactCurrencyText(0, "")
    assertEquals("0", result.text)
    assertTrue(result.spanStyles.isEmpty())
  }
}
