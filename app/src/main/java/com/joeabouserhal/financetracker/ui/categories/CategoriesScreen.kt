package com.joeabouserhal.financetracker.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * Curated category palette — two rows of sensible, readable spending colors
 * (warm/cool/greens/neutrals), followed by a rainbow tile that opens the
 * custom color wheel.
 */
val CATEGORY_SWATCHES =
  listOf(
    "#E8432E", // red
    "#F28C28", // orange
    "#F4C430", // amber
    "#8FBF3F", // lime
    "#4C9A63", // green
    "#2FA39B", // teal
    "#38B6C8", // cyan
    "#3B82F6", // blue
    "#5C7CFA", // indigo
    "#8B5CF6", // violet
    "#9B59B6", // purple
    "#D65DB1", // magenta
    "#E86A92", // pink
    "#8D6E63", // brown
    "#77746C", // warm gray
  )

@Composable
fun CategoriesScreen(
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val scope = rememberCoroutineScope()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  // "ALL" | "EXPENSE" | "INCOME" — ALL is the default.
  var filterName by rememberSaveable { mutableStateOf("ALL") }
  val filterType: TransactionType? =
    when (filterName) {
      "EXPENSE" -> TransactionType.EXPENSE
      "INCOME" -> TransactionType.INCOME
      else -> null
    }

  val allCategories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var search by rememberSaveable { mutableStateOf("") }
  var editing by remember { mutableStateOf<CategoryEntity?>(null) }
  var adding by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  val visibleCategories =
    allCategories
      .filter { filterType == null || it.type == filterType }
      .filter { it.name.contains(search.trim(), ignoreCase = true) }
      .filter { !(it.isDefault && it.name == "Other") } // seeded fallback is invisible

  Column(modifier.fillMaxSize().background(spec.background)) {
    if (onBack != null) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("< BACK", style = MaterialTheme.typography.labelMedium, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onBack))
      }
    }
    ScreenHeader(title = "Categories", subtitle = "EVERY TRANSACTION NEEDS ONE — 'OTHER' IS THE FALLBACK")

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      BrButton(text = "+ Add category", onClick = { adding = true })

      BrTextField(
        value = search,
        onValueChange = { search = it },
        label = "SEARCH CATEGORIES",
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrChip("All", selected = filterName == "ALL", onClick = { filterName = "ALL" })
        BrChip("Expense", selected = filterName == "EXPENSE", onClick = { filterName = "EXPENSE" }, colorDot = spec.expense)
        BrChip("Income", selected = filterName == "INCOME", onClick = { filterName = "INCOME" }, colorDot = spec.income)
      }

      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }

      if (visibleCategories.isEmpty()) {
        Text(
          if (allCategories.isEmpty()) "No categories yet — add one above." else "No categories match your search.",
          style = MaterialTheme.typography.bodySmall,
          color = spec.muted,
        )
      }

      visibleCategories.forEach { category ->
        CategoryRow(
          category = category,
          onEdit = { editing = category },
          onDelete = { deleting = category },
        )
      }

      Spacer(Modifier.height(32.dp))
    }
  }

  if (adding) {
    CategoryDialog(
      title = "ADD CATEGORY",
      initial = null,
      defaultType = filterType ?: TransactionType.EXPENSE,
      onDismiss = { adding = false },
      onSave = { name, color, chosenType ->
        scope.launch {
          try {
            container.categoryRepository.add(ownerId, name, chosenType, color)
            adding = false
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editing?.let { category ->
    CategoryDialog(
      title = "EDIT CATEGORY",
      initial = category,
      defaultType = category.type,
      onDismiss = { editing = null },
      onSave = { name, color, chosenType ->
        scope.launch {
          try {
            container.categoryRepository.update(ownerId, category.id, name, chosenType, color)
            editing = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  deleting?.let { category ->
    BrDialog(
      title = "DELETE CATEGORY?",
      onDismiss = { deleting = null },
      confirmText = "DELETE",
      onConfirm = {
        scope.launch {
          try {
            container.categoryRepository.delete(ownerId, category.id)
            deleting = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    ) {
      Text("Its transactions move to 'Other'.", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
  val spec = LocalThemeSpec.current
  val dotColor by animateColorAsState(
    targetValue = parseColor(category.color),
    animationSpec = tween(200),
    label = "categoryDot",
  )
  Row(
    Modifier.fillMaxWidth().background(spec.surface).padding(horizontal = 12.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(14.dp).background(dotColor).border(1.dp, spec.border))
    Text(
      category.name,
      style = MaterialTheme.typography.bodyMedium,
      color = spec.ink,
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
    )
    if (category.isDefault && category.name == "Other") {
      Text("SEEDED", style = MaterialTheme.typography.labelSmall, color = spec.muted)
    } else {
      Text("EDIT", style = MaterialTheme.typography.labelSmall, color = spec.accent, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onEdit))
      Text("DEL", style = MaterialTheme.typography.labelSmall, color = spec.expense, modifier = Modifier.padding(4.dp).minimumInteractiveComponentSize().clickable(onClick = onDelete))
    }
  }
}

@Composable
private fun CategoryDialog(
  title: String,
  initial: CategoryEntity?,
  defaultType: TransactionType,
  onDismiss: () -> Unit,
  onSave: (name: String, color: String, type: TransactionType) -> Unit,
) {
  val spec = LocalThemeSpec.current
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var color by remember { mutableStateOf(initial?.color ?: CATEGORY_SWATCHES.first()) }
  var dialogType by remember { mutableStateOf(initial?.type ?: defaultType) }
  var showColorWheel by remember { mutableStateOf(false) }
  var dialogError by remember { mutableStateOf<String?>(null) }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    onConfirm = {
      if (name.isBlank()) {
        dialogError = "Category name is required"
      } else {
        onSave(name, color, dialogType)
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      BrSegmentedToggle(
        options = listOf("Expense", "Income"),
        selectedIndex = if (dialogType == TransactionType.EXPENSE) 0 else 1,
        onSelect = { dialogType = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
        optionColors = listOf(spec.expense, spec.income),
      )
      BrTextField(name, { name = it }, "NAME")
      dialogError?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = spec.expense)
      }
      Text("COLOR", style = MaterialTheme.typography.labelSmall, color = spec.muted)

      // Two equal rows of square swatches; the rainbow tile is the LAST tile.
      val tiles: List<String> = CATEGORY_SWATCHES + "RAINBOW"
      tiles.chunked(8).forEach { rowTiles ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          rowTiles.forEach { tile ->
            if (tile == "RAINBOW") {
              RainbowSwatch(onClick = { showColorWheel = true })
            } else {
              Swatch(tile, selected = color == tile, onClick = { color = tile })
            }
          }
        }
      }
    }
  }

  if (showColorWheel) {
    ColorWheelDialog(
      initialHex = color,
      onDismiss = { showColorWheel = false },
      onSelect = {
        color = it
        showColorWheel = false
      },
    )
  }
}

@Composable
private fun RowScope.Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
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
private fun RowScope.RainbowSwatch(onClick: () -> Unit) {
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

private val RAINBOW_HUES =
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
private fun ColorWheelDialog(
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

  Dialog(onDismissRequest = onDismiss) {
    Column(
      Modifier
        .fillMaxWidth()
        .background(spec.surface)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("CUSTOM COLOR", style = MaterialTheme.typography.labelLarge, color = spec.ink)
        Text("✕", style = MaterialTheme.typography.labelLarge, color = spec.muted, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
      }

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

fun parseColor(hex: String): Color {
  val normalized = if (hex.startsWith("#")) hex else "#$hex"
  return try { Color(android.graphics.Color.parseColor(normalized)) } catch (_: IllegalArgumentException) { Color(0xFF77746C) }
}
