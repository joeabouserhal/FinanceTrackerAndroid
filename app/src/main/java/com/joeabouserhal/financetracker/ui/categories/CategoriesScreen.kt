package com.joeabouserhal.financetracker.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.parseHexColor
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
  var filterName by rememberSaveable(ownerId) { mutableStateOf("ALL") }
  val filterType: TransactionType? =
    when (filterName) {
      "EXPENSE" -> TransactionType.EXPENSE
      "INCOME" -> TransactionType.INCOME
      else -> null
    }

  val allCategories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var search by rememberSaveable(ownerId) { mutableStateOf("") }
  var editing by remember { mutableStateOf<CategoryEntity?>(null) }
  var adding by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  CategoryLibrary(
    allCategories, search, { search = it }, filterName, { filterName = it }, onBack,
    onAdd = { error = null; adding = true }, onEdit = { error = null; editing = it },
    modifier = modifier, error = error,
  )

  if (adding) {
    CategoryDialog(
      title = "ADD CATEGORY",
      initial = null,
      defaultType = filterType ?: TransactionType.EXPENSE,
      saveError = error,
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
      saveError = error,
      onDelete = { editing = null; error = null; deleting = category },
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
      error?.let { Text(it, color = spec.expense, style = MaterialTheme.typography.bodySmall) }
    }
  }
}


@Composable
internal fun CategoryDialog(
  title: String,
  initial: CategoryEntity?,
  defaultType: TransactionType,
  onDismiss: () -> Unit,
  onSave: (name: String, color: String, type: TransactionType) -> Unit,
  saveError: String? = null,
  onDelete: (() -> Unit)? = null,
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
    scrollContent = true,
    onConfirm = {
      dialogError = null
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
      (dialogError ?: saveError)?.let {
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
      if (onDelete != null) {
        BrButton("Delete category", onDelete, style = com.joeabouserhal.financetracker.ui.components.BrButtonStyle.DANGER, compact = true)
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

fun parseColor(hex: String): Color = parseHexColor(hex)
