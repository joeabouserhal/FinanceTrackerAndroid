package com.joeabouserhal.financetracker.ui.presets

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.PresetEntity
import com.joeabouserhal.financetracker.data.local.withMutation
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency
import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import com.joeabouserhal.financetracker.data.recurring.RecurrenceCalculator
import com.joeabouserhal.financetracker.data.recurring.RecurrenceDraft
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrChip
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrDialog
import com.joeabouserhal.financetracker.ui.components.BrDatePickerDialog
import com.joeabouserhal.financetracker.ui.components.BrTimePickerDialog
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.utils.Money
import com.joeabouserhal.financetracker.utils.parseHexColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Presets page filter: one type or everything. */
enum class PresetFilter(val type: TransactionType?) {
  ALL(null),
  EXPENSE(TransactionType.EXPENSE),
  INCOME(TransactionType.INCOME),
}

@Composable
fun PresetsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val container = rememberAppContainer()
  val context = LocalContext.current
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  val scope = rememberCoroutineScope()
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
  val schedules by remember(ownerId) { container.recurringRepository.observeSchedules(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  var adding by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<PresetEntity?>(null) }
  var archiving by remember { mutableStateOf<PresetEntity?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  PresetLibrary(
    ownerId, presets, currencies, accounts, categories, schedules.associateBy { it.presetId }, filter, { filter = it }, onBack,
    onSelect = { error = null; editing = it }, modifier = modifier, onAdd = { error = null; adding = true }, error = error,
  )

  if (adding) {
    PresetDialog(
      title = "ADD PRESET",
      initial = null,
      initialSchedule = null,
      defaultType = filter.type ?: TransactionType.EXPENSE,
      saveError = error,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { adding = false },
      onSave = { name, amount, currencyId, accountId, categoryId, presetType, recurrence ->
        scope.launch {
          try {
            container.appDatabase.withMutation {
              val preset = container.presetRepository.add(ownerId, name, presetType, amount, currencyId, categoryId, accountId)
              recurrence?.let { container.recurringRepository.saveSchedule(ownerId, preset.id, it) }
            }
            if (recurrence != null) {
              container.recurringScheduler.requestNow()
              if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            adding = false
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
    )
  }

  editing?.let { preset ->
    PresetDialog(
      title = "EDIT PRESET",
      initial = preset,
      initialSchedule = schedules.firstOrNull { it.presetId == preset.id },
      defaultType = preset.type,
      saveError = error,
      currencies = currencies,
      accounts = accounts,
      categories = categories,
      onDismiss = { editing = null },
      onSave = { name, amount, currencyId, accountId, categoryId, _, recurrence ->
        scope.launch {
          try {
            container.appDatabase.withMutation {
              container.presetRepository.update(ownerId, preset.id, name, amount, currencyId, categoryId, accountId)
              if (recurrence != null) container.recurringRepository.saveSchedule(ownerId, preset.id, recurrence)
              else schedules.firstOrNull { it.presetId == preset.id }?.let { container.recurringRepository.setPaused(ownerId, it.id, true) }
            }
            if (recurrence != null) {
              container.recurringScheduler.requestNow()
              if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            editing = null
            error = null
          } catch (e: Exception) { error = e.message }
        }
      },
      onDelete = { editing = null; error = null; archiving = preset },
    )
  }

  archiving?.let { preset ->
    BrDialog(
      title = "DELETE PRESET?",
      onDismiss = { archiving = null },
      confirmText = "DELETE",
      onConfirm = {
        scope.launch {
          try {
            container.presetRepository.archive(ownerId, preset.id)
            container.recurringScheduler.requestNow()
            archiving = null
            editing = null
            error = null
          } catch (e: Exception) { error = e.message ?: "Could not delete this preset." }
        }
      },
    ) {
      Text("It disappears from the quick-select row but keeps its history.", style = MaterialTheme.typography.bodyMedium)
      error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.expense) }
    }
  }
}

@Composable
internal fun PresetDialog(
  title: String,
  initial: PresetEntity?,
  initialSchedule: RecurringScheduleEntity?,
  defaultType: TransactionType,
  currencies: List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>,
  accounts: List<com.joeabouserhal.financetracker.data.local.entities.AccountEntity>,
  categories: List<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity>,
  onDismiss: () -> Unit,
  onSave: (name: String, amount: Long?, currencyId: String?, accountId: String?, categoryId: String?, type: TransactionType, recurrence: RecurrenceDraft?) -> Unit,
  onDelete: (() -> Unit)? = null,
  saveError: String? = null,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var amountText by remember { mutableStateOf(initial?.defaultAmount?.let { minor -> if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}" } ?: "") }
  var dialogType by remember { mutableStateOf(initial?.type ?: defaultType) }
  var currencyId by remember { mutableStateOf(initial?.defaultCurrencyId) }
  var accountId by remember { mutableStateOf(initial?.defaultAccountId) }
  var categoryId by remember { mutableStateOf(initial?.defaultCategoryId) }
  var dialogError by remember { mutableStateOf<String?>(null) }
  var showCategoryModal by remember { mutableStateOf(false) }
  var categorySearch by remember { mutableStateOf("") }
  var recurring by remember { mutableStateOf(initialSchedule != null && !initialSchedule.paused) }
  var frequency by remember { mutableStateOf(initialSchedule?.frequency ?: RecurrenceFrequency.MONTHLY) }
  var intervalText by remember { mutableStateOf((initialSchedule?.interval ?: 1).toString()) }
  var weekDays by remember { mutableStateOf(initialSchedule?.weekDays?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(LocalDate.now().dayOfWeek.value)) }
  var monthlyRule by remember { mutableStateOf(initialSchedule?.monthlyRule ?: MonthlyRule.DAY_OF_MONTH) }
  var monthlyDayText by remember { mutableStateOf((initialSchedule?.dayOfMonth ?: LocalDate.now().dayOfMonth).toString()) }
  var nthWeekday by remember { mutableStateOf(initialSchedule?.nthWeekday ?: LocalDate.now().dayOfWeek.value) }
  var nthOrdinal by remember { mutableStateOf(initialSchedule?.nthOrdinal ?: 1) }
  var startDateText by remember { mutableStateOf(initialSchedule?.startDate ?: LocalDate.now().toString()) }
  var endDateText by remember { mutableStateOf(initialSchedule?.endDate ?: "") }
  var timeText by remember { mutableStateOf("%02d:%02d".format((initialSchedule?.timeMinutes ?: 540) / 60, (initialSchedule?.timeMinutes ?: 540) % 60)) }
  var pickerTarget by remember { mutableStateOf<RecurringPickerTarget?>(null) }
  val spec = LocalThemeSpec.current

  // "Other" is always first in the list, matching the transaction form.
  val orderedCategories =
    remember(categories, dialogType) {
      categories
        .filter { it.type == dialogType }
        .sortedWith(
          compareBy<com.joeabouserhal.financetracker.data.local.entities.CategoryEntity> {
            if (it.isDefault && it.name == "Other") 0 else 1
          }.thenBy { it.name },
        )
    }

  // New presets start with the default currency and its default account
  // selected, and fall back to the "Other" category, so saving right away
  // already has valid defaults.
  LaunchedEffect(currencies, accounts, orderedCategories) {
    if (initial == null) {
      if (currencyId == null) {
        currencyId = currencies.firstOrNull { it.isDefault }?.id ?: currencies.firstOrNull()?.id
      }
      if (accountId == null) {
        val forCurrency = accounts.filter { it.currencyId == currencyId }
        accountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
      }
      if (categoryId == null) {
        categoryId = orderedCategories.firstOrNull { it.name == "Other" }?.id ?: orderedCategories.firstOrNull()?.id
      }
    }
  }

  // Keep the selected category valid when the dialog's type changes.
  LaunchedEffect(orderedCategories) {
    if (categoryId == null || orderedCategories.none { it.id == categoryId }) {
      categoryId = orderedCategories.firstOrNull { it.name == "Other" }?.id ?: orderedCategories.firstOrNull()?.id
    }
  }

  BrDialog(
    title = title,
    onDismiss = onDismiss,
    scrollContent = true,
    onConfirm = {
      dialogError = null
      if (name.isBlank()) {
        dialogError = "Preset name is required"
      } else {
        val amount = amountText.trim().takeIf { it.isNotBlank() }?.let {
          try { BigDecimal(Money.normalizeDecimalInput(it)).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
        if (amountText.isNotBlank() && (amount == null || amount <= 0)) {
          dialogError = "Amount must be a positive number"
        } else {
          val recurrence = if (!recurring) null else runCatching {
            val interval = intervalText.toInt(); val day = monthlyDayText.toIntOrNull()
            val parts = timeText.split(':'); val minutes = parts[0].toInt() * 60 + parts[1].toInt()
            val startDate = LocalDate.parse(startDateText)
            val endDate = endDateText.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            require(endDate == null || !endDate.isBefore(startDate)) { "End date must be after the start date" }
            RecurrenceDraft(frequency, interval, weekDays, monthlyRule, day, nthWeekday, nthOrdinal, startDateText, endDateText.ifBlank { null }, minutes)
          }.getOrElse { dialogError = "Check the recurrence date and time"; null }
          if (!recurring || recurrence != null) onSave(name, amount, currencyId, accountId, categoryId, dialogType, recurrence)
        }
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (initial == null) {
        BrSegmentedToggle(
          options = listOf("Expense", "Income"),
          selectedIndex = if (dialogType == TransactionType.EXPENSE) 0 else 1,
          onSelect = { dialogType = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
          optionColors = listOf(spec.expense, spec.income),
        )
      }
      BrTextField(name, { name = it }, "NAME")
      BrTextField(amountText, { amountText = it }, "AMOUNT (OPTIONAL)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      (dialogError ?: saveError)?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
      }
      Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        currencies.forEach { c ->
          BrChip(
            c.code,
            selected = currencyId == c.id,
            large = true,
            onClick = {
              currencyId = c.id
              val forCurrency = accounts.filter { it.currencyId == c.id }
              accountId = forCurrency.firstOrNull { it.isDefault }?.id ?: forCurrency.firstOrNull()?.id
            },
          )
        }
      }
      Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.filter { it.currencyId == currencyId }.forEach { a -> BrChip(a.name, selected = accountId == a.id, large = true, onClick = { accountId = a.id }) }
      }
      Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        BrChip("ALL ▸", selected = false, onClick = { categorySearch = ""; showCategoryModal = true })
        orderedCategories.forEach { c -> BrChip(c.name, selected = categoryId == c.id, onClick = { categoryId = c.id }, colorDot = parsePresetColor(c.color)) }
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Text("MAKE RECURRING", style = MaterialTheme.typography.labelSmall, color = spec.ink)
          Text("Auto-add on a schedule", style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
        Switch(checked = recurring, onCheckedChange = { recurring = it })
      }
      if (recurring) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.42f)))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("SCHEDULE", style = MaterialTheme.typography.labelLarge, color = spec.ink)
          Text("HOW OFTEN", style = MaterialTheme.typography.labelSmall, color = spec.muted)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RecurrenceFrequency.entries.forEach { item ->
              BrChip(item.name, selected = frequency == item, onClick = { frequency = item })
            }
          }
          BrTextField(
            intervalText,
            { intervalText = it.filter(Char::isDigit) },
            "REPEAT EVERY ${frequency.name.lowercase()}",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
          if (frequency == RecurrenceFrequency.WEEKLY) {
            Text("ON DAYS", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
              listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                BrChip(label, selected = index + 1 in weekDays, onClick = {
                  weekDays = if (index + 1 in weekDays) weekDays - (index + 1) else weekDays + (index + 1)
                })
              }
            }
          }
          if (frequency == RecurrenceFrequency.MONTHLY) {
            Text("MONTHLY RULE", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
              listOf(MonthlyRule.DAY_OF_MONTH, MonthlyRule.LAST_DAY, MonthlyRule.LAST_BUSINESS_DAY, MonthlyRule.NTH_WEEKDAY).forEach { rule ->
                BrChip(rule.name.replace('_', ' '), selected = monthlyRule == rule, onClick = { monthlyRule = rule })
              }
            }
            if (monthlyRule == MonthlyRule.DAY_OF_MONTH) {
              BrTextField(monthlyDayText, { monthlyDayText = it.filter(Char::isDigit) }, "DAY OF MONTH", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (monthlyRule == MonthlyRule.NTH_WEEKDAY) {
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").forEachIndexed { index, label ->
                  BrChip(label, selected = nthWeekday == index + 1, onClick = { nthWeekday = index + 1 })
                }
              }
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("1ST", "2ND", "3RD", "4TH").forEachIndexed { index, label ->
                  val ordinal = index + 1
                  BrChip(label, selected = nthOrdinal == ordinal, onClick = { nthOrdinal = ordinal })
                }
              }
            }
          }
          Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border.copy(alpha = 0.42f)))
          Text("WHEN", style = MaterialTheme.typography.labelLarge, color = spec.ink)
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringPickerField("STARTS", startDateText, Modifier.weight(1f)) { pickerTarget = RecurringPickerTarget.START_DATE }
            RecurringPickerField("ENDS", endDateText.ifBlank { "NO END" }, Modifier.weight(1f)) { pickerTarget = RecurringPickerTarget.END_DATE }
          }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("REMINDER", style = MaterialTheme.typography.labelSmall, color = spec.muted)
              Text(timeText, style = MaterialTheme.typography.titleMedium, color = spec.ink)
            }
            BrButton("SET TIME", { pickerTarget = RecurringPickerTarget.TIME }, style = BrButtonStyle.OUTLINE, compact = true, fillWidth = false)
          }
          if (endDateText.isNotBlank()) {
            BrChip("CLEAR END DATE", selected = false, onClick = { endDateText = "" })
          }
          val preview = runCatching { RecurrenceCalculator.next(RecurrenceDraft(frequency, intervalText.toIntOrNull() ?: 1, weekDays, monthlyRule, monthlyDayText.toIntOrNull(), nthWeekday, nthOrdinal, startDateText, endDateText.ifBlank { null }, 540).let { draft -> RecurringScheduleEntity("preview", "", "", draft.frequency, draft.interval, draft.weekDays.joinToString(","), draft.monthlyRule, draft.dayOfMonth, draft.nthWeekday, draft.nthOrdinal, draft.startDate, draft.endDate, draft.timeMinutes, false, null, "", "") }, LocalDate.now()) }.getOrNull()
          Text(if (preview == null) "CHECK THE SCHEDULE DETAILS" else "NEXT: $preview · $timeText", style = MaterialTheme.typography.labelSmall, color = spec.accent)
        }
      }
      if (initial != null && onDelete != null) {
        Text(
          "DELETE PRESET",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onDelete)
            .padding(vertical = 4.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }

  if (showCategoryModal) {
    BrDialog(
      title = "All categories",
      onDismiss = { showCategoryModal = false },
      dismissText = null,
      wide = true,
    ) {
      Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BrTextField(
          value = categorySearch,
          onValueChange = { categorySearch = it },
          label = "SEARCH CATEGORIES",
          modifier = Modifier.fillMaxWidth(),
        )
        val filtered = orderedCategories.filter { it.name.contains(categorySearch, ignoreCase = true) }
        if (filtered.isEmpty()) {
          Text("No categories match", style = MaterialTheme.typography.labelSmall, color = spec.muted)
        } else {
          LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 320.dp),
            contentPadding = PaddingValues(vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(filtered, key = { it.id }) { category ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable {
                    categoryId = category.id
                    showCategoryModal = false
                  }
                  .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  Modifier
                    .size(14.dp)
                    .background(parsePresetColor(category.color)),
                )
                Text(
                  category.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (categoryId == category.id) spec.accent else spec.ink,
                  modifier = Modifier.padding(horizontal = 12.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  when (pickerTarget) {
    RecurringPickerTarget.START_DATE -> BrDatePickerDialog(
      title = "Start date",
      initialDate = LocalDate.parse(startDateText),
      onDismiss = { pickerTarget = null },
      onSelect = { chosen ->
        startDateText = chosen.toString()
        val currentEnd = endDateText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (currentEnd != null && currentEnd.isBefore(chosen)) endDateText = chosen.toString()
        pickerTarget = null
      },
    )
    RecurringPickerTarget.END_DATE -> BrDatePickerDialog(
      title = "End date",
      initialDate = endDateText.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.parse(startDateText),
      minimumDate = LocalDate.parse(startDateText),
      onDismiss = { pickerTarget = null },
      onSelect = { chosen ->
        endDateText = chosen.toString()
        pickerTarget = null
      },
    )
    RecurringPickerTarget.TIME -> BrTimePickerDialog(
      initialMinutes = timeText.split(':').let { parts ->
        ((parts.getOrNull(0)?.toIntOrNull() ?: 9) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(0, 1439)
      },
      onDismiss = { pickerTarget = null },
      onSelect = { minutes ->
        timeText = "%02d:%02d".format(minutes / 60, minutes % 60)
        pickerTarget = null
      },
    )
    null -> Unit
  }
}

private fun parsePresetColor(hex: String): androidx.compose.ui.graphics.Color = parseHexColor(hex)

private enum class RecurringPickerTarget { START_DATE, END_DATE, TIME }

@Composable
private fun RecurringPickerField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val spec = LocalThemeSpec.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    Box(
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
      Text(value, style = MaterialTheme.typography.labelMedium, color = spec.ink)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(spec.border))
  }
}
