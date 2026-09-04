package com.joeabouserhal.financetracker.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.*
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.transactions.*
import com.joeabouserhal.financetracker.utils.Money
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PeriodSaver = listSaver<ReportPeriod, String>(
  save = { listOf(it.kind.name, it.month.toString(), it.custom?.start?.toString().orEmpty(), it.custom?.end?.toString().orEmpty()) },
  restore = { values -> ReportPeriod(ReportPeriodKind.valueOf(values[0]), YearMonth.parse(values[1]),
    if (values[2].isNotEmpty()) ReportWindow(LocalDate.parse(values[2]), LocalDate.parse(values[3])) else null) },
)

internal fun ReportPeriod.label(today: LocalDate): String = when (kind) {
  ReportPeriodKind.ALL -> "All time"
  ReportPeriodKind.MONTH -> if (month == YearMonth.from(today)) "This month" else month.format(DateTimeFormatter.ofPattern("MMM yyyy"))
  ReportPeriodKind.CUSTOM -> requireNotNull(custom).label()
}

internal fun ReportWindow.label(): String {
  val format = DateTimeFormatter.ofPattern("d MMM yyyy")
  return if (start == end) start.format(format) else "${start.format(format)} – ${end.format(format)}"
}

@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
  val container = rememberAppContainer()
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = null)
  val owner = session?.ownerId ?: return
  var today by remember { mutableStateOf(LocalDate.now()) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
  val data by remember(owner) {
    combine(
      container.transactionRepository.observeAll(owner),
      container.categoryRepository.observeByType(owner, TransactionType.INCOME),
      container.categoryRepository.observeByType(owner, TransactionType.EXPENSE),
      container.accountRepository.observeActive(owner),
      container.currencyRepository.observeAll(owner),
    ) { transactions, income, expense, accounts, currencies ->
      owner to Triple(enrichTransactions(transactions, income + expense, accounts, currencies), currencies, accounts)
    }
  }.collectAsStateWithLifecycle(initialValue = null)
  // StateFlow collection may briefly retain the previous flow's value during
  // an owner switch. Never render that snapshot in the new account partition.
  val current = data?.takeIf { it.first == owner }?.second
  if (current == null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading report…") }
    return
  }
  ReportContent(ownerId = owner, items = current.first, currencies = current.second, today = today,
    activeAccountCurrencies = current.third.associate { it.id to it.currencyId }, modifier = modifier,
    refinementPanel = { filters, update, type ->
      FilterPanel(filters, update, typeHint = type, showTypeRow = false, showDateRow = false, showSortRow = false, showCurrencyRow = false)
    })
}

/** UI fixture-friendly: no database, navigation, or network dependencies. */
@Composable
internal fun ReportContent(
  ownerId: String,
  items: List<TransactionListItem>,
  currencies: List<CurrencyEntity>,
  today: LocalDate,
  activeAccountCurrencies: Map<String, String> = emptyMap(),
  modifier: Modifier = Modifier,
  refinementPanel: @Composable (TransactionFilterState, (TransactionFilterState) -> Unit, TypeFilter) -> Unit = { _, _, _ -> },
) {
  val spec = LocalThemeSpec.current
  var savedOwner by rememberSaveable { mutableStateOf(ownerId) }
  var period by rememberSaveable(stateSaver = PeriodSaver) { mutableStateOf(ReportPeriod(month = YearMonth.from(today))) }
  var filters by rememberSaveable(stateSaver = TransactionFilterStateSaver) { mutableStateOf(TransactionFilterState()) }
  var viewType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
  var filtersOpen by rememberSaveable { mutableStateOf(false) }
  var periodOpen by rememberSaveable { mutableStateOf(false) }
  var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
  var detailCurrency by rememberSaveable { mutableStateOf<String?>(null) }
  var detailCategory by rememberSaveable { mutableStateOf<String?>(null) }
  var currencyChoice by rememberSaveable { mutableStateOf<String?>(null) }
  if (savedOwner != ownerId) {
    savedOwner = ownerId
    period = ReportPeriod(month = YearMonth.from(today))
    filters = TransactionFilterState()
    viewType = TransactionType.EXPENSE
    filtersOpen = false
    periodOpen = false
    expanded = emptyList()
    detailCurrency = null
    detailCategory = null
    currencyChoice = null
  }
  val currencyOptions = currencies.sortedByDescending { it.isDefault }
  val selectedCurrency = currencyOptions.firstOrNull { it.id == currencyChoice } ?: currencyOptions.firstOrNull()
  val allowedAccounts = activeAccountCurrencies.filterValues { it == selectedCurrency?.id }.keys
  val reportFilters = filters.copy(currencyIds = selectedCurrency?.let { setOf(it.id) }.orEmpty(),
    accountIds = filters.accountIds.intersect(allowedAccounts))
  LaunchedEffect(selectedCurrency?.id, activeAccountCurrencies) {
    currencyChoice = selectedCurrency?.id
    filters = filters.copy(currencyIds = emptySet(), accountIds = filters.accountIds.intersect(allowedAccounts))
  }
  val reports = remember(items, currencies, reportFilters, period, viewType, today) {
    buildReports(items, currencies, viewType, period, reportFilters, today)
  }
  val activeCount = reportRefinementCount(reportFilters.copy(currencyIds = emptySet()))
  val listState = key(ownerId) { rememberLazyListState() }
  val tone = if (viewType == TransactionType.EXPENSE) spec.expense else spec.income
  LazyColumn(state = listState, modifier = modifier.fillMaxSize().background(spec.background).testTag("report-list"),
    contentPadding = PaddingValues(bottom = 28.dp)) {
    item("header") { ScreenHeader(title = "Report") }
    reportSection("period") {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (period.kind == ReportPeriodKind.MONTH) {
          PeriodArrow("Previous month", "‹") { period = period.copy(month = period.month.minusMonths(1)); expanded = emptyList() }
        }
        Column(Modifier.weight(1f).clickable(role = Role.Button) { periodOpen = true }.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(period.label(today), style = MaterialTheme.typography.titleMedium, color = spec.ink)
          Text(if (period.kind == ReportPeriodKind.MONTH && period.month == YearMonth.from(today)) "Through ${today.format(DateTimeFormatter.ofPattern("d MMM"))} · Change range" else "Change range",
            style = MaterialTheme.typography.bodySmall, color = spec.muted)
        }
        if (period.kind == ReportPeriodKind.MONTH) {
          PeriodArrow("Next month", "›", enabled = period.month < YearMonth.from(today)) { period = period.copy(month = period.month.plusMonths(1)); expanded = emptyList() }
        }
      }
    }
    reportSection("controls") {
      ReportControls(
        viewType = viewType, filtersOpen = filtersOpen, activeCount = activeCount,
        onToggleFilters = { filtersOpen = !filtersOpen },
        onSelect = { next ->
          if (next != viewType) { viewType = next; filters = filters.copy(categoryIds = emptySet()); expanded = emptyList() }
        },
      )
    }
    if (currencyOptions.isNotEmpty()) reportSection("currency-selector") {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        .testTag("report-currency-selector"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        currencyOptions.forEach { currency ->
          BrChip(currency.code, selectedCurrency?.id == currency.id, {
            if (currencyChoice != currency.id) {
              currencyChoice = currency.id
              filters = filters.copy(accountIds = emptySet(), currencyIds = emptySet())
              detailCategory = null
              detailCurrency = null
            }
          }, modifier = Modifier.testTag("report-currency:${currency.id}").semantics { selected = selectedCurrency?.id == currency.id }, comfortable = true)
        }
      }
    }
    if (filtersOpen) reportSection("filters") {
      refinementPanel(reportFilters, { filters = it.copy(currencyIds = emptySet()); expanded = emptyList() }, if (viewType == TransactionType.EXPENSE) TypeFilter.EXPENSE else TypeFilter.INCOME)
    }
    if (reports.isEmpty()) reportSection("empty") {
      EmptyState(if (items.isEmpty()) "No transactions yet — add some to see a report."
        else "No ${if (viewType == TransactionType.EXPENSE) "spending" else "earning"}${selectedCurrency?.let { " in ${it.code}" }.orEmpty()} in this range${if (activeCount > 0) " with these filters" else ""}.", Modifier.padding(16.dp))
    }
    reports.forEach { report ->
      reportSection("summary:${report.currencyId}") { ReportHeadline(report, viewType, period, today) }
      reportSection("chart:${report.currencyId}") { key(period, viewType, filters) { TrendChart(report, tone, Modifier.padding(horizontal = 16.dp)) } }
      reportSection("categories:${report.currencyId}") { Text("BY CATEGORY", style = MaterialTheme.typography.labelSmall, color = spec.muted, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) }
      val shown = if (report.currencyId in expanded) report.categories else report.categories.take(5)
      itemsIndexed(shown, key = { _, category -> "category:${report.currencyId}:${category.id}" }) { categoryIndex, category ->
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(spec.surface)
          .testTag("report-category:${report.currencyId}:${category.id}")) {
          if (categoryIndex > 0) TransactionDashedDivider(Modifier.testTag("report-category-divider:${report.currencyId}:${category.id}"))
          CategoryReportRow(category, report.symbol, Modifier) { detailCurrency = report.currencyId; detailCategory = category.id }
        }
      }
      if (report.categories.size > 5) reportSection("expand:${report.currencyId}") {
        Text(if (report.currencyId in expanded) "Show less" else "Show all categories (${report.categories.size})", style = MaterialTheme.typography.labelMedium, color = spec.accent,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(role = Role.Button) {
            expanded = if (report.currencyId in expanded) expanded - report.currencyId else expanded + report.currencyId
          }.padding(vertical = 14.dp))
      }
    }
  }
  if (periodOpen) ReportPeriodDialog(period, today, onDismiss = { periodOpen = false }, onSelect = { period = it; periodOpen = false; expanded = emptyList() })
  if (detailCategory != null) {
    val report = reports.firstOrNull { it.currencyId == detailCurrency }
    val category = report?.categories?.firstOrNull { it.id == detailCategory }
    CategoryDetailsDialog(category, report?.symbol.orEmpty(), period.label(today), onDismiss = { detailCategory = null; detailCurrency = null })
  }
}

/** Space report sections, but keep individual category rows contiguous and lazy. */
private fun LazyListScope.reportSection(key: String, content: @Composable () -> Unit) {
  item(key) {
    Column {
      Spacer(Modifier.height(12.dp))
      content()
    }
  }
}

@Composable
private fun ReportControls(viewType: TransactionType, filtersOpen: Boolean, activeCount: Int, onToggleFilters: () -> Unit, onSelect: (TransactionType) -> Unit) {
  val spec = LocalThemeSpec.current
  val toggle: @Composable (Modifier) -> Unit = { modifier ->
        BrSegmentedToggle(options = listOf("SPENDING", "EARNING"), selectedIndex = if (viewType == TransactionType.EXPENSE) 0 else 1,
          onSelect = { onSelect(if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME) }, optionColors = listOf(spec.expense, spec.income), modifier = modifier)
  }
  val filter: @Composable () -> Unit = {
        BrButton(if (activeCount > 0) "FILTER $activeCount" else "FILTER", onClick = onToggleFilters,
          style = if (filtersOpen || activeCount > 0) BrButtonStyle.SOLID else BrButtonStyle.OUTLINE, compact = true, fillWidth = false, minHeight = 48.dp)
  }
  BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    if (maxWidth / LocalDensity.current.fontScale < 280.dp) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
        toggle(Modifier.fillMaxWidth())
        filter()
      }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      toggle(Modifier.weight(1f))
      filter()
    }
  }
}

@Composable
private fun PeriodArrow(description: String, glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  Box(Modifier.size(48.dp).semantics { contentDescription = description }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
    Text(glyph, fontSize = 26.sp, color = if (enabled) spec.ink else spec.muted.copy(alpha = 0.4f))
  }
}

@Composable
private fun ReportHeadline(report: CurrencyReport, viewType: TransactionType, period: ReportPeriod, today: LocalDate) {
  val spec = LocalThemeSpec.current
  val tone = if (viewType == TransactionType.EXPENSE) spec.expense else spec.income
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("${report.code} · ${report.name}", style = MaterialTheme.typography.labelMedium, color = spec.muted)
    Text(if (viewType == TransactionType.EXPENSE) "TOTAL SPENT" else "TOTAL EARNED", style = MaterialTheme.typography.labelSmall, color = spec.muted)
    Text(compactCurrencyText(report.totalMinor, report.symbol), style = MaterialTheme.typography.headlineLarge, color = tone, fontWeight = FontWeight.Bold)
    Text("${report.count} transaction${if (report.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = spec.muted)
    if (report.previousTotalMinor != null) {
      val comparisonLabel = if (period.kind == ReportPeriodKind.MONTH && period.month == YearMonth.from(today)) "same period last month" else if (period.kind == ReportPeriodKind.MONTH) "last month" else "previous period"
      val change = report.changePercent
      Text(when {
        change == null -> "No previous activity"
        kotlin.math.abs(change) < 0.05 -> "Unchanged vs $comparisonLabel"
        else -> "${String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(change))}% ${if (change > 0) "more" else "less"} vs $comparisonLabel"
      }, style = MaterialTheme.typography.bodySmall, color = spec.muted)
    }
  }
}

@Composable
private fun TrendChart(report: CurrencyReport, tone: Color, modifier: Modifier = Modifier) {
  val spec = LocalThemeSpec.current
  var selected by rememberSaveable(report.currencyId) { mutableStateOf<Int?>(null) }
  val buckets = report.buckets
  val selectedBucket = selected?.let { buckets.getOrNull(it) }
  val maximum = buckets.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth().height(120.dp).testTag("trend:${report.currencyId}"), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
      buckets.forEachIndexed { index, bucket ->
        val fraction = (bucket.amountMinor.toDouble() / maximum).toFloat().coerceIn(0f, 1f)
        Box(Modifier.weight(1f).fillMaxHeight().testTag("bucket:${report.currencyId}:$index").semantics { contentDescription = "${bucket.window.label()}: ${Money.format(bucket.amountMinor, report.symbol)}" }
          .clickable(role = Role.Button) { selected = index }, contentAlignment = Alignment.BottomCenter) {
          Box(Modifier.widthIn(max = 14.dp).fillMaxWidth().height(if (fraction == 0f) 1.dp else maxOf(2.dp, 112.dp * fraction))
            .background(if (fraction == 0f) spec.border.copy(alpha = 0.35f) else tone.copy(alpha = if (selected == null || selected == index) 1f else 0.35f)))
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      val dateFormat = DateTimeFormatter.ofPattern("d MMM yy")
      Text(buckets.firstOrNull()?.window?.start?.format(dateFormat).orEmpty(), style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(buckets.lastOrNull()?.window?.end?.format(dateFormat).orEmpty(), style = MaterialTheme.typography.labelSmall, color = spec.muted)
    }
    Text(selectedBucket?.let { "${it.window.label()} · ${Money.format(it.amountMinor, report.symbol)}" } ?: "Tap a bar to inspect activity", style = MaterialTheme.typography.bodySmall, color = spec.muted, modifier = Modifier.heightIn(min = 32.dp))
  }
}

@Composable
private fun CategoryReportRow(category: CategoryReport, symbol: String, modifier: Modifier, onClick: () -> Unit) {
  val spec = LocalThemeSpec.current
  val color = parseCategoryColor(category.color)
  Column(modifier.fillMaxWidth().background(spec.surface).clickable(role = Role.Button, onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.size(10.dp).background(color))
      Text(category.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink, modifier = Modifier.weight(1f))
      Text(String.format(Locale.getDefault(), "%.1f%%", category.share * 100), style = MaterialTheme.typography.labelSmall, color = spec.muted)
    }
    Text(compactCurrencyText(category.amountMinor, symbol), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = spec.ink)
    Box(Modifier.fillMaxWidth().height(3.dp).background(spec.border.copy(alpha = 0.18f))) {
      Box(Modifier.fillMaxWidth(category.share.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
  }
}

@Composable
internal fun CategoryDetailsDialog(category: CategoryReport?, symbol: String, periodLabel: String, onDismiss: () -> Unit) {
  val spec = LocalThemeSpec.current
  val bodyHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.52f).coerceAtMost(460.dp)
  BrDialog(title = category?.name ?: "Category details", onDismiss = onDismiss, dismissText = "CLOSE", wide = true) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = bodyHeight).testTag("category-transactions")) {
      item("summary") {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(periodLabel, style = MaterialTheme.typography.bodySmall, color = spec.muted)
          if (category != null) {
            Text(compactCurrencyText(category.amountMinor, symbol), style = MaterialTheme.typography.titleLarge, color = spec.ink)
            Text("${String.format(Locale.getDefault(), "%.1f%%", category.share * 100)} of total · ${category.transactions.size} transactions", style = MaterialTheme.typography.bodySmall, color = spec.muted)
          } else Text("No matching transactions remain in this report.", style = MaterialTheme.typography.bodyMedium, color = spec.muted)
        }
      }
      itemsIndexed(category?.transactions.orEmpty(), key = { _, it -> it.transaction.id }) { index, transaction ->
        Column { if (index > 0) TransactionDashedDivider(); TransactionRow(transaction, onPress = null) }
      }
    }
  }
}
