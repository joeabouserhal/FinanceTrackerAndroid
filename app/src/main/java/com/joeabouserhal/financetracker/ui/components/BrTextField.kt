package com.joeabouserhal.financetracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Outlined text field forced into the brutalist look: sharp corners, 2dp border, mono text. */
@Composable
fun BrTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  singleLine: Boolean = true,
  keyboardOptions: KeyboardOptions =
    KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
  visualTransformation: VisualTransformation = VisualTransformation.None,
  suffix: (@Composable () -> Unit)? = null,
  @DrawableRes leadingIconRes: Int? = null,
  @DrawableRes trailingIconRes: Int? = null,
  trailingIconDescription: String? = null,
  onTrailingIconClick: (() -> Unit)? = null,
) {
  val spec = LocalThemeSpec.current
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    enabled = enabled,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    singleLine = singleLine,
    shape = RectangleShape,
    keyboardOptions = keyboardOptions,
    visualTransformation = visualTransformation,
    suffix = suffix,
    leadingIcon = leadingIconRes?.let { iconRes ->
      {
        Icon(
          painter = painterResource(iconRes),
          contentDescription = null,
          tint = spec.muted,
          modifier = Modifier.size(20.dp),
        )
      }
    },
    trailingIcon = trailingIconRes?.let { iconRes ->
      {
        if (onTrailingIconClick != null) {
          IconButton(onClick = onTrailingIconClick) {
            Icon(
              painter = painterResource(iconRes),
              contentDescription = trailingIconDescription,
              tint = spec.muted,
              modifier = Modifier.size(22.dp),
            )
          }
        } else {
          Icon(
            painter = painterResource(iconRes),
            contentDescription = trailingIconDescription,
            tint = spec.muted,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    },
    textStyle = MaterialTheme.typography.bodyLarge.copy(color = spec.ink),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedTextColor = spec.ink,
        unfocusedTextColor = spec.ink,
        disabledTextColor = spec.muted,
        focusedBorderColor = spec.accent,
        unfocusedBorderColor = spec.border,
        disabledBorderColor = spec.muted,
        focusedLabelColor = spec.accent,
        unfocusedLabelColor = spec.muted,
        cursorColor = spec.accent,
        focusedContainerColor = spec.surface,
        unfocusedContainerColor = spec.surface,
        disabledContainerColor = spec.surfaceAlt,
      ),
  )
}
