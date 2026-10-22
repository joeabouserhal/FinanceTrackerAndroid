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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joeabouserhal.financetracker.theme.LocalThemeSpec

/** Keeps the quieter modal controls scoped to dialogs, not the surrounding screens. */
internal val LocalModalStyle = staticCompositionLocalOf { false }

/** One flat surface, a clear title, and a single primary action. */
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
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .systemBarsPadding().imePadding()
          .padding(horizontal = if (wide) 10.dp else 22.dp, vertical = 24.dp),
      contentAlignment = Alignment.Center,
    ) {
      CompositionLocalProvider(LocalModalStyle provides true) {
        Column(
          modifier
            .widthIn(max = if (wide) 600.dp else 520.dp)
            .fillMaxWidth()
            .background(spec.surface)
            .border(1.dp, spec.border.copy(alpha = 0.55f))
            .graphicsLayer {
              alpha = enterProgress.value
              scaleX = 0.97f + (enterProgress.value * 0.03f)
              scaleY = 0.97f + (enterProgress.value * 0.03f)
            },
        ) {
          Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              title.uppercase(),
              style = MaterialTheme.typography.labelLarge,
              color = spec.ink,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(
              Modifier
                .size(48.dp)
                .semantics { contentDescription = "Close dialog" }
                .clickable(role = Role.Button, onClick = onDismiss),
              contentAlignment = Alignment.Center,
            ) {
              Text("×", style = MaterialTheme.typography.titleLarge, color = spec.muted)
            }
          }

          Column(
            Modifier
              .weight(1f, fill = false)
              .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
              .fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            content()
          }

          if (onConfirm != null || dismissText != null) {
            Row(
              Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (dismissText != null) {
                BrButton(
                  text = dismissText,
                  onClick = onDismiss,
                  style = BrButtonStyle.OUTLINE,
                  compact = true,
                  minHeight = 48.dp,
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
                  minHeight = 48.dp,
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
