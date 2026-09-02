package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Brutalist-styled alert dialog wrapper (surface background, mono title). */
@Composable
fun BrDialog(
  title: String,
  onDismiss: () -> Unit,
  modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
  confirmText: String = "SAVE",
  onConfirm: (() -> Unit)? = null,
  confirmEnabled: Boolean = true,
  /** Pass null to hide the dismiss button entirely. */
  dismissText: String? = "CANCEL",
  content: @Composable () -> Unit,
) {
  val spec = LocalThemeSpec.current
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = modifier.border(1.dp, spec.border),
    containerColor = spec.surface,
    titleContentColor = spec.ink,
    textContentColor = spec.ink,
    title = { Text(title.uppercase(), style = MaterialTheme.typography.labelLarge) },
    text = { Column { content() } },
    confirmButton = {
      if (onConfirm != null) {
        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
          Text(
            confirmText,
            style = MaterialTheme.typography.labelMedium,
            color = if (confirmEnabled) spec.accent else spec.muted,
          )
        }
      }
    },
    dismissButton = {
      if (dismissText != null) {
        TextButton(onClick = onDismiss) {
          Text(dismissText, style = MaterialTheme.typography.labelMedium, color = spec.muted)
        }
      }
    },
  )
}
