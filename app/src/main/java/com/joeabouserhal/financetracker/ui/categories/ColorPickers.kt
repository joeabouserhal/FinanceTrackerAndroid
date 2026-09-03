package com.joeabouserhal.financetracker.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.launch

@Composable
fun RowScope.Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Box(
    Modifier
      .weight(1f)
      .aspectRatio(1f)
      .background(parseColor(hex))
      .border(width = if (selected) 2.dp else 1.dp, color = if (selected) spec.accent else spec.border)
      .clickable(onClick = onClick),
  )
}

@Composable
fun RowScope.RainbowSwatch(onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Box(
    Modifier
      .weight(1f)
      .aspectRatio(1f)
      .background(Brush.sweepGradient(RAINBOW_HUES))
      .border(1.dp, spec.border)
      .clickable(onClick = onClick),
  )
}

internal val RAINBOW_HUES =
  listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
  )

/** Custom color picker: HSV wheel (hue + saturation), brightness slider,
 * hex entry, and the last 8 used custom colors. */
@Composable
fun ColorWheelDialog(
  initialHex: String,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  val spec = LocalThemeSpec.current
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val history by container.settingsRepository.customColorHistory.collectAsStateWithLifecycle(initialValue = emptyList())

  val initialHsv = FloatArray(3)
  android.graphics.Color.colorToHSV(parseColor(initialHex).toArgb(), initialHsv)

  var hue by remember { mutableStateOf(initialHsv[0]) }
  var sat by remember { mutableStateOf(initialHsv[1]) }
  var value by remember { mutableStateOf(initialHsv[2]) }
  var hexText by remember { mutableStateOf(initialHex.removePrefix("#").uppercase().take(6)) }

  fun hexOfRgb(rgb: Int): String = rgb.toString(16).padStart(6, '0').uppercase()

  fun selectedHex(): String = hexOfRgb(Color.hsv(hue, sat, value).toArgb() and 0xFFFFFF)

  fun applyHex(hex: String) {
    val parsed = parseColor(hex)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
    hue = hsv[0]
    sat = hsv[1]
    value = hsv[2]
    hexText = hex.removePrefix("#").uppercase().take(6)
  }

  fun updateFromPosition(pos: Offset, sizePx: Float) {
    val center = sizePx / 2f
    val dx = pos.x - center
    val dy = pos.y - center
    val dist = sqrt(dx * dx + dy * dy)
    hue = ((atan2(dy, dx) * 180.0 / PI).toFloat() + 360f) % 360f
    sat = (dist / center).coerceIn(0f, 1f)
    hexText = selectedHex()
  }

  BrDialog(
    title = "Custom color",
    onDismiss = onDismiss,
    dismissText = null,
    wide = true,
  ) {
    Column(
      Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(240.dp)
          .pointerInput(Unit) {
            detectTapGestures { pos -> updateFromPosition(pos, size.width.toFloat()) }
          }
          .pointerInput(Unit) {
            detectDragGestures { change, _ -> updateFromPosition(change.position, size.width.toFloat()) }
          },
        contentAlignment = Alignment.Center,
      ) {
        Canvas(Modifier.size(220.dp)) {
          val radius = size.minDimension / 2f
          val center = Offset(size.width / 2f, size.height / 2f)
          drawCircle(Brush.sweepGradient(RAINBOW_HUES, center = center), radius = radius, center = center)
          drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = radius), radius = radius, center = center)

          // Indicator: hue/saturation position + selected color core.
          val angle = hue / 180.0 * PI
          val indicator = Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius * sat,
            center.y + kotlin.math.sin(angle).toFloat() * radius * sat,
          )
          drawCircle(Color.White, radius = 9.dp.toPx(), center = indicator)
          drawCircle(Color.hsv(hue, sat, value), radius = 7.dp.toPx(), center = indicator)
          drawCircle(spec.border, radius = 9.dp.toPx(), center = indicator, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          Modifier
            .size(40.dp)
            .background(Color.hsv(hue, sat, value))
            .border(1.dp, spec.border),
        )
        BrTextField(
          value = hexText,
          onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() || it in 'A'..'F' }.uppercase().take(6)
            hexText = cleaned
            if (cleaned.length == 6) applyHex("#$cleaned")
          },
          label = "HEX (#RRGGBB)",
          modifier = Modifier.weight(1f),
        )
      }

      Text("BRIGHTNESS", style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Slider(
        value = value,
        onValueChange = {
          value = it
          hexText = selectedHex()
        },
        valueRange = 0f..1f,
        colors =
          SliderDefaults.colors(
            thumbColor = spec.accent,
            activeTrackColor = spec.accent,
            inactiveTrackColor = spec.border,
          ),
      )

      if (history.isNotEmpty()) {
        Text("RECENT", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        // Always 8 slots so recent swatches stay the same size as the palette.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          history.forEach { hex ->
            Box(
              Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(parseColor(hex))
                .border(1.dp, spec.border)
                .clickable { applyHex(hex) },
            )
          }
          repeat((8 - history.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
          }
        }
      }

      BrButton(
        text = "USE COLOR",
        onClick = {
          val hex = selectedHex()
          scope.launch { container.settingsRepository.addCustomColor("#$hex") }
          onSelect("#$hex")
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
