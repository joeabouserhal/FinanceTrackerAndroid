package com.joeabouserhal.financetracker.utils

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Small, deterministic calculator for transaction amounts. It deliberately
 * accepts only numbers, parentheses, and the four basic operators—there is no
 * dynamic-code evaluation involved.
 */
sealed interface AmountExpressionResult {
  data object NotExpression : AmountExpressionResult

  /** The result is rounded once, at the cents precision the app can save. */
  data class Valid(val value: BigDecimal) : AmountExpressionResult

  data class Invalid(val message: String) : AmountExpressionResult
}

object AmountExpression {
  private val operators = setOf('+', '-', '*', '/', 'x', 'X', '×', '(', ')')

  fun evaluate(raw: String): AmountExpressionResult {
    val input = raw.trim()
    if (input.none { it in operators }) return AmountExpressionResult.NotExpression
    if (input.isBlank()) return AmountExpressionResult.NotExpression

    return try {
      val value = Parser(input.replace(',', '.')).parse()
      val rounded = value.setScale(2, RoundingMode.HALF_UP)
      rounded.movePointRight(2).longValueExact()
      when {
        rounded <= BigDecimal.ZERO -> AmountExpressionResult.Invalid("Result must be positive")
        else -> AmountExpressionResult.Valid(rounded)
      }
    } catch (error: ExpressionError) {
      AmountExpressionResult.Invalid(error.message ?: "Finish the equation")
    } catch (_: ArithmeticException) {
      AmountExpressionResult.Invalid("Result is too large")
    }
  }

  /** Canonical input text after the user accepts a calculated result. */
  fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

  private class Parser(private val source: String) {
    private var index = 0

    fun parse(): BigDecimal {
      val result = expression()
      skipWhitespace()
      if (index != source.length) fail("Use only numbers and +, −, ×, ÷")
      return result
    }

    private fun expression(): BigDecimal {
      var result = term()
      while (true) {
        skipWhitespace()
        result = when {
          take('+') -> result + term()
          take('-') -> result - term()
          else -> return result
        }
      }
    }

    private fun term(): BigDecimal {
      var result = factor()
      while (true) {
        skipWhitespace()
        result = when {
          take('*') || take('x') || take('X') || take('×') -> result * factor()
          take('/') -> {
            val divisor = factor()
            if (divisor.compareTo(BigDecimal.ZERO) == 0) fail("Cannot divide by zero")
            result.divide(divisor, MathContext.DECIMAL128)
          }
          else -> return result
        }
      }
    }

    private fun factor(): BigDecimal {
      skipWhitespace()
      when {
        take('+') -> return factor()
        take('-') -> return factor().negate()
        take('(') -> {
          val nested = expression()
          skipWhitespace()
          if (!take(')')) fail("Finish the equation")
          return nested
        }
      }

      val start = index
      var hasDigit = false
      var hasDecimal = false
      while (index < source.length) {
        when (val char = source[index]) {
          in '0'..'9' -> {
            hasDigit = true
            index++
          }
          '.' -> {
            if (hasDecimal) fail("Use one decimal point per number")
            hasDecimal = true
            index++
          }
          else -> break
        }
      }
      if (!hasDigit) fail("Finish the equation")
      return try {
        BigDecimal(source.substring(start, index))
      } catch (_: NumberFormatException) {
        fail("Enter a valid number")
      }
    }

    private fun take(char: Char): Boolean {
      skipWhitespace()
      if (index < source.length && source[index] == char) {
        index++
        return true
      }
      return false
    }

    private fun skipWhitespace() {
      while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun fail(message: String): Nothing = throw ExpressionError(message)
  }

  private class ExpressionError(message: String) : IllegalArgumentException(message)
}
