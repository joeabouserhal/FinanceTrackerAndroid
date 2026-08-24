package com.joeabouserhal.financetracker.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandsSeparatorTransformationTest {
  private val transformation = ThousandsSeparatorTransformation()

  private fun transform(raw: String): String = transformation.filter(AnnotatedString(raw)).text.text

  @Test
  fun `groups thousands with commas`() {
    assertEquals("1,000", transform("1000"))
    assertEquals("1,000,000", transform("1000000"))
    assertEquals("12,345", transform("12345"))
  }

  @Test
  fun `leaves short numbers and decimals alone`() {
    assertEquals("999", transform("999"))
    assertEquals("12.5", transform("12.5"))
    assertEquals("1,234.56", transform("1234.56"))
    assertEquals("", transform(""))
  }

  @Test
  fun `offset mapping is comma aware`() {
    // 1234 -> "1,234": original offset 4 must map to transformed length 5.
    val t = transformation.filter(AnnotatedString("1234"))
    assertEquals(5, t.offsetMapping.originalToTransformed(4))
    assertEquals(4, t.offsetMapping.transformedToOriginal(5))
    // Original offset 1 ("1|234") -> transformed offset 1 ("1|,234").
    assertEquals(1, t.offsetMapping.originalToTransformed(1))
    // Transformed offset 2 ("1,|234") -> original offset 1.
    assertEquals(1, t.offsetMapping.transformedToOriginal(2))
  }
}
