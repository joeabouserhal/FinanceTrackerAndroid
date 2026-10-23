package com.joeabouserhal.financetracker.ui.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joeabouserhal.financetracker.R
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

val LocalAmountsHidden = staticCompositionLocalOf { false }

/** Blur only display amounts, never editable form values. Also redact accessibility text. */
@Composable
fun PrivateAmount(text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
  style: TextStyle = MaterialTheme.typography.bodyLarge, fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null) {
  val hidden = LocalAmountsHidden.current
  val fallback = LocalThemeSpec.current.muted.copy(alpha = 0.3f)
  val conceal = if (!hidden) Modifier else Modifier.clearAndSetSemantics { contentDescription = "Amount hidden" }
    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp, BlurredEdgeTreatment.Unbounded)
      else Modifier.drawWithContent { drawRoundRect(fallback, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())) })
  Text(text, modifier.then(conceal), color = color, style = style, fontWeight = fontWeight,
    maxLines = maxLines, overflow = overflow, textAlign = textAlign)
}

@Composable
fun PrivateAmount(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
  style: TextStyle = MaterialTheme.typography.bodyLarge, fontWeight: FontWeight? = null,
  maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null) =
  PrivateAmount(AnnotatedString(text), modifier, color, style, fontWeight, maxLines, overflow, textAlign)

@Composable
fun AmountVisibilityToggle() {
  val hidden = LocalAmountsHidden.current
  val settings = rememberAppContainer().settingsRepository
  val scope = rememberCoroutineScope()
  IconButton(onClick = { scope.launch { settings.setAmountsHidden(!hidden) } }) {
    Icon(painterResource(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
      contentDescription = if (hidden) "Show amounts" else "Hide amounts", tint = LocalThemeSpec.current.muted)
  }
}
