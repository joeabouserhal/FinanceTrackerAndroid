package com.joeabouserhal.financetracker.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.CategoryEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import kotlinx.coroutines.launch

val CATEGORY_SWATCHES =
  listOf(
    "#4C9A63", "#E8432E", "#F4C430", "#77746C", "#3B82F6",
    "#8B5CF6", "#EC4899", "#14B8A6", "#F97316", "#0EA5E9",
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

  var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
  val categories by remember(ownerId, type) { container.categoryRepository.observeByType(ownerId, type) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var search by rememberSaveable { mutableStateOf("") }
  var editing by remember { mutableStateOf<CategoryEntity?>(null) }
  var adding by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  val visibleCategories = categories
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
        BrChip("Expense", selected = type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE }, colorDot = spec.expense)
        BrChip("Income", selected = type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME }, colorDot = spec.income)
      }

      error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = spec.expense) }

      if (visibleCategories.isEmpty()) {
        Text(
          if (categories.isEmpty()) "No categories yet — add one above." else "No categories match your search.",
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
      type = type,
      onDismiss = { adding = false },
      onSave = { name, color ->
        scope.launch {
          try {
            container.categoryRepository.add(ownerId, name, type, color)
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
      type = category.type,
      onDismiss = { editing = null },
      onSave = { name, color ->
        scope.launch {
          try {
            container.categoryRepository.update(ownerId, category.id, name, color)
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
  type: TransactionType,
  onDismiss: () -> Unit,
  onSave: (name: String, color: String) -> Unit,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var color by remember { mutableStateOf(initial?.color ?: CATEGORY_SWATCHES.first()) }

  BrDialog(title = title, onDismiss = onDismiss, onConfirm = { onSave(name, color) }) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      BrTextField(name, { name = it }, "NAME")
      Text("COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CATEGORY_SWATCHES.forEach { swatch ->
          val selected = swatch == color
          Box(
            Modifier
              .size(28.dp)
              .background(parseColor(swatch))
              .border(width = if (selected) 2.dp else 1.dp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
              .clickable { color = swatch },
          )
        }
      }
    }
  }
}

fun parseColor(hex: String): Color =
  try { Color(android.graphics.Color.parseColor(hex)) } catch (_: IllegalArgumentException) { Color(0xFF77746C) }
