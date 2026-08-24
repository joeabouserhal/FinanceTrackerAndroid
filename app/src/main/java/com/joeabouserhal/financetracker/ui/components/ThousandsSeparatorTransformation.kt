package com.joeabouserhal.financetracker.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Displays digit input grouped by thousands (1,000,000) while the underlying
 * text stays ungrouped, so parsing/validation never sees commas.
 *
 * Only commas are inserted (characters are never removed), and the offset
 * mapping is comma-aware so the caret never lands out of bounds.
 */
class ThousandsSeparatorTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val raw = text.text
    val transformed = formatGrouped(raw)
    return TransformedText(
      AnnotatedString(transformed),
      ThousandsSeparatorOffsetMapping(raw, transformed),
    )
  }

  private fun formatGrouped(raw: String): String {
    val dotIndex = raw.indexOf('.')
    val head = if (dotIndex == -1) raw else raw.substring(0, dotIndex)
    val tail = if (dotIndex == -1) "" else raw.substring(dotIndex)

    val out = StringBuilder()
    var i = 0
    while (i < head.length) {
      if (head[i].isDigit()) {
        val start = i
        while (i < head.length && head[i].isDigit()) i++
        val digits = head.substring(start, i)
        out.append(digits.reversed().chunked(3).joinToString(",").reversed())
      } else {
        out.append(head[i])
        i++
      }
    }
    return out.toString() + tail
  }
}

/** Maps offsets between the ungrouped source and the comma-grouped display text. */
private class ThousandsSeparatorOffsetMapping(
  private val original: String,
  private val transformed: String,
) : OffsetMapping {
  override fun originalToTransformed(offset: Int): Int {
    var originalIndex = 0
    var transformedIndex = 0
    while (originalIndex < offset && transformedIndex < transformed.length) {
      if (transformed[transformedIndex] == ',') {
        transformedIndex++
        continue
      }
      originalIndex++
      transformedIndex++
    }
    return transformedIndex
  }

  override fun transformedToOriginal(offset: Int): Int {
    val bounded = offset.coerceIn(0, transformed.length)
    var originalIndex = 0
    var transformedIndex = 0
    while (transformedIndex < bounded) {
      if (transformed[transformedIndex] == ',') {
        transformedIndex++
        continue
      }
      originalIndex++
      transformedIndex++
    }
    return originalIndex.coerceAtMost(original.length)
  }
}
