package com.joeabouserhal.financetracker.utils

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountExpressionTest {

  @Test
  fun `a plain amount remains a plain amount`() {
    assertTrue(AmountExpression.evaluate("12.50") is AmountExpressionResult.NotExpression)
    assertTrue(AmountExpression.evaluate(" 12,50 ") is AmountExpressionResult.NotExpression)
  }

  @Test
  fun `evaluates addition subtraction and multiplication aliases`() {
    assertValue("12.50 + 7.25", "19.75")
    assertValue("19 - 4.5", "14.5")
    assertValue("6 x 1.5", "9")
    assertValue("6 × 1.5", "9")
  }

  @Test
  fun `multiplication and division take precedence over addition and subtraction`() {
    assertValue("2 + 3 * 4", "14")
    assertValue("18 / 3 + 2", "8")
  }

  @Test
  fun `division produces a monetary result rounded half up to cents`() {
    assertValue("10 / 3", "3.33")
    assertValue("1 / 6", "0.17")
  }

  @Test
  fun `rejects incomplete invalid and division by zero expressions`() {
    assertTrue(AmountExpression.evaluate("12 +") is AmountExpressionResult.Invalid)
    assertTrue(AmountExpression.evaluate("12 + hello") is AmountExpressionResult.Invalid)
    assertTrue(AmountExpression.evaluate("10 / 0") is AmountExpressionResult.Invalid)
  }

  private fun assertValue(raw: String, expected: String) {
    val result = AmountExpression.evaluate(raw)
    assertTrue("Expected a valid expression for '$raw', got $result", result is AmountExpressionResult.Valid)
    assertEquals(0, BigDecimal(expected).compareTo((result as AmountExpressionResult.Valid).value))
  }
}
