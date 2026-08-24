package com.joeabouserhal.financetracker.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.data.repositories.enrichTransactions
import com.joeabouserhal.financetracker.data.session.Session
import com.joeabouserhal.financetracker.theme.LocalThemeSpec
import com.joeabouserhal.financetracker.ui.components.BrButton
import com.joeabouserhal.financetracker.ui.components.BrButtonStyle
import com.joeabouserhal.financetracker.ui.components.BrSegmentedToggle
import com.joeabouserhal.financetracker.ui.components.BrTextField
import com.joeabouserhal.financetracker.ui.components.EmptyState
import com.joeabouserhal.financetracker.ui.components.ScreenHeader
import com.joeabouserhal.financetracker.ui.rememberAppContainer
import com.joeabouserhal.financetracker.ui.transactions.FilterPanel
import com.joeabouserhal.financetracker.ui.transactions.TransactionFilterState
import com.joeabouserhal.financetracker.ui.transactions.TransactionFilterStateSaver
import com.joeabouserhal.financetracker.ui.transactions.TransactionFiltering
import com.joeabouserhal.financetracker.ui.transactions.TypeFilter
import com.joeabouserhal.financetracker.ui.transactions.parseCategoryColor
import com.joeabouserhal.financetracker.utils.Money
import kotlinx.coroutines.flow.combine

/** One category's share of the selected view, for the donut + breakdown. */
private data class CategorySlice(
  val name: String,
  val color: androidx.compose.ui.graphics.Color,
  val amountMinor: Long,
  val count: Int,
)

/** Aggregated report numbers for one currency (after filters). */
private data class CurrencyReport(
  val code: String,
  val name: String,
  val symbol: String,
  val incomeMinor: Long,
  val expenseMinor: Long,
  val expenseSlices: List<CategorySlice>,
  val incomeSlices: List<CategorySlice>,
  val count: Int,
)

@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
  val container = rememberAppContainer()
  val spec = LocalThemeSpec.current
  val session by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = Session())
  val ownerId = session.ownerId

  var filters by rememberSaveable(stateSaver = TransactionFilterStateSaver) { mutableStateOf(TransactionFilterState()) }
  var filtersOpen by rememberSaveable { mutableStateOf(false) }
  var viewType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }

  val items by remember(ownerId) {
    combine(
      container.transactionRepository.observeAll(ownerId),
      container.categoryRepository.observeByType(ownerId, TransactionType.INCOME),
      container.categoryRepository.observeByType(ownerId, TransactionType.EXPENSE),
      container.accountRepository.observeActive(ownerId),
      container.currencyRepository.observeAll(ownerId),
    ) { txs, incomeCategories, expenseCategories, accounts, currencies ->
      enrichTransactions(txs, incomeCategories + expenseCategories, accounts, currencies)
    }
  }.collectAsStateWithLifecycle(initialValue = emptyList())

  val currencies by remember(ownerId) { container.currencyRepository.observeAll(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val accounts by remember(ownerId) { container.accountRepository.observeActive(ownerId) }
    .collectAsStateWithLifecycle(initialValue = emptyList())

  // Same normalization as the Transactions page: stale currency/account ids
  // in the saved filter state get repaired.
  LaunchedEffect(currencies, accounts) {
    if (currencies.isEmpty()) return@LaunchedEffect
    val selectedIds =
      if (filters.currencyIds.all { id -> currencies.any { it.id == id } }) filters.currencyIds else emptySet()
    val visibleAccountIds =
      accounts.filter { selectedIds.isEmpty() || it.currencyId in selectedIds }.map { it.id }.toSet()
    val accountIds = filters.accountIds.intersect(visibleAccountIds)
    if (selectedIds != filters.currencyIds || accountIds != filters.accountIds) {
      filters = filters.copy(currencyIds = selectedIds, accountIds = accountIds)
    }
  }

  val visible = remember(items, filters) { TransactionFiltering.apply(items, filters) }
  val activeCount = TransactionFiltering.activeCount(filters)

  val reports = remember(visible, currencies) { buildReports(visible, currencies) }

  Column(
    modifier.fillMaxSize().background(spec.background),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ScreenHeader(title = "Report")

    Column(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BrButton(
        text = if (activeCount > 0) "FILTERS ($activeCount)" else "FILTERS",
        onClick = { filtersOpen = !filtersOpen },
        style = if (filtersOpen || activeCount > 0) BrButtonStyle.SOLID else BrButtonStyle.OUTLINE,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    AnimatedVisibility(
      visible = filtersOpen,
      enter = expandVertically(tween(200)) + fadeIn(tween(200)),
      exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // View toggle lives inside the expanded filter area.
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("VIEW", style = MaterialTheme.typography.labelSmall, color = spec.muted)
          BrSegmentedToggle(
            options = listOf("SPENDING", "EARNING"),
            selectedIndex = if (viewType == TransactionType.EXPENSE) 0 else 1,
            onSelect = { viewType = if (it == 0) TransactionType.EXPENSE else TransactionType.INCOME },
            optionColors = listOf(spec.expense, spec.income),
          )
        }
        FilterPanel(
          filters = filters,
          onFiltersChange = { filters = it },
          typeHint = if (viewType == TransactionType.EXPENSE) TypeFilter.EXPENSE else TypeFilter.INCOME,
          showTypeRow = false,
        )
      }
    }

    if (visible.isEmpty()) {
      EmptyState(
        message = if (items.isEmpty()) "No transactions yet — add some to see a report." else "No transactions match your filters.",
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
      return@Column
    }

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      reports.forEach { report ->
        ReportCurrencySection(report = report, viewType = viewType)
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

private fun buildReports(
  items: List<TransactionListItem>,
  currencies: List<com.joeabouserhal.financetracker.data.local.entities.CurrencyEntity>,
): List<CurrencyReport> {
  val currencyById = currencies.associateBy { it.id }
  return items
    .groupBy { it.transaction.currencyId }
    .mapNotNull { (currencyId, currencyItems) ->
      val currency = currencyById[currencyId] ?: return@mapNotNull null
      fun slices(type: TransactionType): List<CategorySlice> =
        currencyItems
          .filter { it.transaction.type == type }
          .groupBy { it.categoryName to it.categoryColor }
          .map { (key, group) ->
            CategorySlice(
              name = key.first,
              color = parseCategoryColor(key.second),
              amountMinor = group.sumOf { it.transaction.amount },
              count = group.size,
            )
          }
          .sortedByDescending { it.amountMinor }

      CurrencyReport(
        code = currency.code,
        name = currency.name,
        symbol = currency.symbol,
        incomeMinor = currencyItems.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount },
        expenseMinor = currencyItems.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount },
        expenseSlices = slices(TransactionType.EXPENSE),
        incomeSlices = slices(TransactionType.INCOME),
        count = currencyItems.size,
      )
    }
}

@Composable
private fun ReportCurrencySection(report: CurrencyReport, viewType: TransactionType) {
  val spec = LocalThemeSpec.current
  val slices = if (viewType == TransactionType.EXPENSE) report.expenseSlices else report.incomeSlices
  val total = if (viewType == TransactionType.EXPENSE) report.expenseMinor else report.incomeMinor
  val label = if (viewType == TransactionType.EXPENSE) "SPENT" else "EARNED"

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${report.code} · ${report.name}",
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
      )
      Text(
        "${report.count} TRANSACTION${if (report.count == 1) "" else "S"}",
        style = MaterialTheme.typography.labelSmall,
        color = spec.muted,
      )
    }

    // Per-currency totals strip.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      TotalsTile("IN", report.incomeMinor, report.symbol, spec.income, Modifier.weight(1f))
      TotalsTile("OUT", report.expenseMinor, report.symbol, spec.expense, Modifier.weight(1f))
      TotalsTile(
        "NET",
        report.incomeMinor - report.expenseMinor,
        report.symbol,
        if (report.incomeMinor >= report.expenseMinor) spec.income else spec.expense,
        Modifier.weight(1f),
      )
    }

    if (slices.isEmpty()) {
      Text(
        "No ${if (viewType == TransactionType.EXPENSE) "spending" else "earning"} to chart for this currency.",
        style = MaterialTheme.typography.bodySmall,
        color = spec.muted,
      )
    } else {
      DonutChart(
        slices = slices,
        totalMinor = total,
        centerLabel = label,
        symbol = report.symbol,
        color = if (viewType == TransactionType.EXPENSE) spec.expense else spec.income,
      )

      // Per-category breakdown under the chart.
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slices.forEach { slice ->
          val percent = if (total > 0) slice.amountMinor.toFloat() / total.toFloat() * 100f else 0f
          Row(
            Modifier.fillMaxWidth().background(spec.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.size(12.dp).background(slice.color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(slice.name, style = MaterialTheme.typography.bodyMedium, color = spec.ink)
              Text("${slice.count} transaction${if (slice.count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = spec.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(Money.format(slice.amountMinor, report.symbol), style = MaterialTheme.typography.bodyMedium, color = spec.ink)
              Text(
                "%.1f%%".format(percent),
                style = MaterialTheme.typography.labelSmall,
                color = spec.muted,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TotalsTile(label: String, amountMinor: Long, symbol: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
  val spec = LocalThemeSpec.current
  Column(
    modifier.background(spec.surface).padding(horizontal = 10.dp, vertical = 10.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = spec.muted)
    Text(
      Money.format(amountMinor, symbol),
      style = MaterialTheme.typography.bodyMedium,
      color = color,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** Donut chart with a small gap between slices and the total in the middle. */
@Composable
private fun DonutChart(
  slices: List<CategorySlice>,
  totalMinor: Long,
  centerLabel: String,
  symbol: String,
  color: androidx.compose.ui.graphics.Color,
) {
  val spec = LocalThemeSpec.current
  Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(200.dp)) {
      val strokeWidth = 30.dp.toPx()
      val inset = strokeWidth / 2f + 2.dp.toPx()
      val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
      val topLeft = Offset(inset, inset)
      var startAngle = -90f
      slices.forEach { slice ->
        val sweep = if (totalMinor > 0) slice.amountMinor.toFloat() / totalMinor.toFloat() * 360f else 0f
        if (sweep > 0f) {
          drawArc(
            color = slice.color,
            startAngle = startAngle,
            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
          )
        }
        startAngle += sweep
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = spec.muted)
      Text(
        Money.format(totalMinor, symbol),
        style = MaterialTheme.typography.titleLarge,
        color = color,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
