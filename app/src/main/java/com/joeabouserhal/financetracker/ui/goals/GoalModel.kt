package com.joeabouserhal.financetracker.ui.goals

import com.joeabouserhal.financetracker.data.local.entities.*
import com.joeabouserhal.financetracker.utils.Money
import com.joeabouserhal.financetracker.data.repositories.netAmount
import com.joeabouserhal.financetracker.data.repositories.accountBalanceMap
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.roundToInt

internal data class GoalProgress(
  val goal: GoalEntity,
  val currency: CurrencyEntity?,
  val account: AccountEntity?,
  val progressMinor: Long,
) {
  val ready: Boolean get() = !goal.completed && goal.targetMinor > 0 && progressMinor >= goal.targetMinor
  val fraction: Float get() = if (goal.completed) 1f else if (goal.targetMinor <= 0) 0f
    else (progressMinor.toDouble() / goal.targetMinor).coerceIn(0.0, 1.0).toFloat()
  val percent: Int get() = (fraction * 100).roundToInt()
  val scopeLabel: String get() = if (goal.accountId == null) "All ${currency?.code.orEmpty()} accounts"
    else "${account?.name ?: "Unavailable account"}${if (account?.archived == true) " (archived)" else ""} · ${currency?.code.orEmpty()}"
}

internal data class GoalSnapshot(
  val progress: List<GoalProgress>,
  val currencies: List<CurrencyEntity>,
  val accounts: List<AccountEntity>,
  val balances: Map<String, Long>,
)

internal data class CompletionNotice(val goalName: String, val symbol: String, val deductions: List<Pair<String, Long>>)

internal fun buildProgress(
  goals: List<GoalEntity>, transactions: List<TransactionEntity>,
  currencies: List<CurrencyEntity>, accounts: List<AccountEntity>,
): List<GoalProgress> {
  val currencyById = currencies.associateBy { it.id }
  val accountById = accounts.associateBy { it.id }
  val netByAccount = buildAccountBalances(transactions, accounts)
  val netByCurrency = transactions.groupBy { it.currencyId }.mapValues { (_, rows) -> rows.sumOf { it.netAmount() } }
  return goals.map { goal ->
    GoalProgress(goal, currencyById[goal.currencyId], accountById[goal.accountId],
      if (goal.accountId != null) netByAccount[goal.accountId] ?: 0 else
        (netByCurrency[goal.currencyId] ?: 0) + accounts.filter { it.currencyId == goal.currencyId && it.deletedAt == null }.sumOf { it.openingBalanceMinor })
  }
}

internal fun buildAccountBalances(transactions: List<TransactionEntity>, accounts: List<AccountEntity>): Map<String, Long> {
  val net = accountBalanceMap(transactions, accounts)
  return accounts.associate { it.id to (net[it.id] ?: 0L) }
}

internal fun minorToText(minor: Long): String = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

internal fun textToMinor(text: String): Long = if (text.isBlank()) 0 else try {
  BigDecimal(Money.normalizeDecimalInput(text.trim())).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
} catch (_: NumberFormatException) { Long.MIN_VALUE } catch (_: ArithmeticException) { Long.MIN_VALUE }

/** No silent wraparound when several individually valid amounts exceed Long.MAX_VALUE. */
internal fun allocationTotal(values: Collection<Long>): Long? {
  if (values.any { it < 0 }) return null
  val total = values.fold(BigInteger.ZERO) { sum, value -> sum + BigInteger.valueOf(value) }
  return if (total <= BigInteger.valueOf(Long.MAX_VALUE)) total.toLong() else null
}

/** Suggest a split using positive balances, default account first; never invent an overdraft. */
internal fun suggestAllocations(target: Long, accounts: List<AccountEntity>, balances: Map<String, Long>): Map<String, Long> {
  var remaining = target.coerceAtLeast(0)
  return accounts.sortedByDescending { it.isDefault }.associate { account ->
    val amount = minOf(remaining, (balances[account.id] ?: 0).coerceAtLeast(0))
    remaining -= amount
    account.id to amount
  }
}
