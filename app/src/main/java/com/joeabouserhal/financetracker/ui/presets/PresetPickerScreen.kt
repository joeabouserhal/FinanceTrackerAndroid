package com.joeabouserhal.financetracker.ui.presets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.rememberAppContainer

/**
 * "Add from preset" page: pick a preset with an All/Expense/Income filter.
 * Selecting one opens the add-transaction form pre-filled from it.
 */
@Composable
fun PresetPickerScreen(
  onBack: () -> Unit,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filter by rememberSaveable(ownerId) { mutableStateOf(PresetFilter.ALL) }
  val filterType = filter.type
  val presets by remember(ownerId) {
    container.presetRepository.observeAll(ownerId)
  }.collectAsStateWithLifecycle(initialValue = emptyList())
  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by remember(ownerId) { container.categoryRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  PresetLibrary(ownerId, presets, currencies, accounts, categories, filter, { filter = it }, onBack,
    onSelect = { onPick(it.id) }, modifier = modifier)
}
