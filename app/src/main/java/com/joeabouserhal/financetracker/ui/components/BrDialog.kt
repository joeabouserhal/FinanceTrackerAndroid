package com.joeabouserhal.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/**
 * Shared modal surface for every confirmation, form, and picker in the app.
 * A raised header and dedicated action dock give the content hierarchy
 * without relying on thick borders or stacked shadow layers.
 */
@Composable
fun BrDialog(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  confirmText: String = "SAVE",
  onConfirm: (() -> Unit)? = null,
  confirmEnabled: Boolean = true,
  /** Pass null to hide the dismiss action; the header close control remains. */
  dismissText: String? = "CANCEL",
  /** Pickers can use the extra width while ordinary forms remain focused. */
  wide: Boolean = false,
  /** Forms can scroll while their title and Save/Cancel actions remain visible. */
  scrollContent: Boolean = false,
  /** Lets a content-heavy form finish its first layout before becoming visible. */
  settleBeforeEnter: Boolean = false,
  content: @Composable () -> Unit,
) {
  val spec = LocalThemeSpec.current
  val destructive = confirmText.contains("DELETE", ignoreCase = true) || confirmText.contains("REMOVE", ignoreCase = true)
  val enterProgress = remember { Animatable(if (settleBeforeEnter) 0f else 1f) }

  LaunchedEffect(settleBeforeEnter) {
    if (settleBeforeEnter) {
      enterProgress.snapTo(0f)
      withFrameNanos { }
      withFrameNanos { }
      enterProgress.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
    } else {
      enterProgress.snapTo(1f)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = !scrollContent),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .then(if (scrollContent) Modifier.systemBarsPadding().imePadding() else Modifier)
          .padding(horizontal = if (wide) 10.dp else 22.dp, vertical = 24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier
          .fillMaxWidth()
          .widthIn(max = if (wide) 600.dp else 520.dp),
      ) {
        Column(
          Modifier
            .fillMaxWidth()
            .background(spec.surface)
            .border(spec.borderWidth, spec.border)
            .graphicsLayer {
              alpha = enterProgress.value
              scaleX = 0.97f + (enterProgress.value * 0.03f)
              scaleY = 0.97f + (enterProgress.value * 0.03f)
            },
        ) {
          Row(
            Modifier.fillMaxWidth().background(spec.surfaceAlt).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              title.uppercase(),
              style = MaterialTheme.typography.labelLarge,
              color = spec.ink,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
            )
            Box(
              Modifier
                .size(34.dp)
                .background(spec.surface)
                .border(spec.borderWidth, spec.border)
                .clickable(onClick = onDismiss),
              contentAlignment = Alignment.Center,
            ) {
              Text("×", style = MaterialTheme.typography.titleMedium, color = spec.muted)
            }
          }

          Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.42f)))
          Column(
            Modifier
              .then(if (scrollContent) Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()) else Modifier)
              .fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            content()
          }

          if (onConfirm != null || dismissText != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.42f)))
            Row(
              Modifier.fillMaxWidth().background(spec.surfaceAlt).padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (dismissText != null) {
                BrButton(
                  text = dismissText,
                  onClick = onDismiss,
                  style = BrButtonStyle.OUTLINE,
                  compact = true,
                  modifier = Modifier.weight(1f),
                )
              }
              if (onConfirm != null) {
                BrButton(
                  text = confirmText,
                  onClick = onConfirm,
                  enabled = confirmEnabled,
                  style = if (destructive) BrButtonStyle.DANGER else BrButtonStyle.SOLID,
                  compact = true,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }
  }
}
