package com.joeabouserhal.financetracker.ui.goals

import com.joeabouserhal.financetracker.data.local.entities.*
import org.junit.Assert.*
import org.junit.Test

class GoalModelTest {
  private val currency = CurrencyEntity("usd", "owner", "USD", "$", "Dollar", true, "", "")
  private val accounts = listOf(
    AccountEntity("cash", "owner", "usd", "Cash", createdAt = "", updatedAt = ""),
    AccountEntity("bank", "owner", "usd", "Bank", isDefault = true, createdAt = "", updatedAt = ""),
    AccountEntity("old", "owner", "usd", "Old", archived = true, createdAt = "", updatedAt = ""),
  )
  private fun goal(account: String? = null, completed: Boolean = false) =
    GoalEntity("goal", "owner", "Travel", 10000, "usd", account, completed, "", "")
  private fun tx(id: String, amount: Long, type: TransactionType, account: String?, currency: String = "usd") =
    TransactionEntity(id, "owner", type, amount, currency, "category", account, "2026-09-04", null, null, null, null, "", "")

  @Test fun progressUsesScopeAndTreatsGoalWithdrawalsAsSpending() {
    val rows = listOf(tx("income", 20000, TransactionType.INCOME, "bank"), tx("expense", 2000, TransactionType.EXPENSE, "bank"),
      tx("goal", 3000, TransactionType.GOAL, "bank"), tx("cash", 7000, TransactionType.INCOME, "cash"),
      tx("old", 1000, TransactionType.INCOME, "old"), tx("other", 90000, TransactionType.INCOME, null, "eur"))
    val progress = buildProgress(listOf(goal(), goal("bank"), goal("old")), rows, listOf(currency), accounts)
    assertEquals(listOf(23000L, 15000L, 1000L), progress.map { it.progressMinor })
    assertTrue(progress.last().scopeLabel.contains("archived"))
  }

  @Test fun unassignedTransactionsCountOnlyTowardWholeCurrency() {
    val rows = listOf(tx("income", 5000, TransactionType.INCOME, null))
    assertEquals(listOf(5000L, 0L), buildProgress(listOf(goal(), goal("bank")), rows, listOf(currency), accounts).map { it.progressMinor })
  }

  @Test fun meterClampsNegativeAndOverTargetBalances() {
    val negative = GoalProgress(goal(), currency, null, -100)
    assertEquals(0, negative.percent)
    assertFalse(negative.ready)
    val over = negative.copy(progressMinor = Long.MAX_VALUE)
    assertEquals(100, over.percent)
    assertTrue(over.ready)
  }

  @Test fun completedGoalRemainsAchievedAfterItsBalanceChanges() {
    val progress = GoalProgress(goal(completed = true), currency, null, -50000)
    assertEquals(100, progress.percent)
    assertEquals(1f, progress.fraction, 0f)
    assertFalse(progress.ready)
  }

  @Test fun missingAccountNeverAppearsAsAllAccounts() {
    assertTrue(GoalProgress(goal("gone"), currency, null, 0).scopeLabel.startsWith("Unavailable account"))
  }

  @Test fun splitUsesDefaultFirstAndDoesNotOverdraw() {
    assertEquals(mapOf("bank" to 6000L, "cash" to 4000L), suggestAllocations(10000, accounts.take(2), mapOf("cash" to 9000, "bank" to 6000)))
    assertEquals(mapOf("bank" to 0L, "cash" to 1000L), suggestAllocations(10000, accounts.take(2), mapOf("cash" to 1000, "bank" to -500)))
  }

  @Test fun allocationTotalsRejectInvalidNegativeAndOverflow() {
    assertEquals(10000L, allocationTotal(listOf(3000L, 7000L)))
    assertNull(allocationTotal(listOf(-1L, 10001L)))
    assertNull(allocationTotal(listOf(Long.MAX_VALUE, 1L)))
    assertEquals(Long.MAX_VALUE, allocationTotal(listOf(Long.MAX_VALUE, 0L)))
  }

  @Test fun amountsRoundExactlyAndRejectUnrepresentableInput() {
    assertEquals(101L, textToMinor("1.005"))
    assertEquals(0L, textToMinor(""))
    assertEquals(Long.MIN_VALUE, textToMinor("not money"))
    assertEquals(Long.MIN_VALUE, textToMinor("99999999999999999999"))
    listOf(-105L, 0L, 100L, 125L, Long.MAX_VALUE).forEach { assertEquals(it, textToMinor(minorToText(it))) }
  }

  @Test fun zeroAccountsAreIncludedInBalanceLookup() {
    assertEquals(mapOf("cash" to 0L, "bank" to 0L, "old" to 0L), buildAccountBalances(emptyList(), accounts))
  }
}
