package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import com.joeabouserhal.financetracker.utils.TransactionCsv
import org.junit.Assert.*
import org.junit.Test

class TransactionConvenienceTest {
  private fun transaction(type: TransactionType = TransactionType.EXPENSE) = TransactionEntity(
    id = "original", ownerId = "owner", type = type, amount = 105025, currencyId = "usd",
    categoryId = "food", accountId = "bank", destinationAccountId = if (type == TransactionType.TRANSFER) "cash" else null,
    date = "2025-01-02", title = "Coffee, cake", notes = "Client lunch\nsecond line", presetId = "preset", goalId = "goal",
    createdAt = "2025-01-02T00:00:00Z", updatedAt = "2025-01-02T00:00:00Z", recurrenceOccurrenceId = "occurrence")
  private fun item(type: TransactionType = TransactionType.EXPENSE) = TransactionListItem(transaction(type),
    "Food", "#FF0000", "$", "Bank", "Cash", "USD")

  @Test fun `duplicate preserves editable fields but uses today`() {
    val source = transaction()
    val draft = TransactionDraft.duplicate(source, "2026-10-23")
    assertEquals("2026-10-23", draft.date)
    assertEquals(source.amount, draft.amount)
    assertEquals(source.title, draft.title)
    assertEquals(source.notes, draft.notes)
    assertEquals(source.accountId, draft.accountId)
    assertEquals("2025-01-02", source.date)
  }
  @Test fun `goal copy is an ordinary expense and transfer copy retains both legs`() {
    assertEquals(TransactionType.EXPENSE, TransactionDraft.duplicate(transaction(TransactionType.GOAL), "2026-10-23").type)
    val transfer = TransactionDraft.duplicate(transaction(TransactionType.TRANSFER), "2026-10-23")
    assertEquals(TransactionType.TRANSFER, transfer.type)
    assertEquals("bank", transfer.accountId)
    assertEquals("cash", transfer.destinationAccountId)
  }
  @Test fun `deleted source cannot be duplicated`() {
    assertThrows(IllegalArgumentException::class.java) { TransactionDraft.duplicate(transaction().copy(deletedAt = "gone"), "2026-10-23") }
  }
  @Test fun `search matches combined amount category account notes currency and title`() {
    listOf("1050.25", "$1,050.25", "1050,25", "USD", "food", "bank", "cash", "client lunch",
      "coffee bank 1050.25", "expense USD", "-1050.25").forEach {
      assertTrue(it, TransactionFiltering.matchesSearch(item(), it))
    }
    assertFalse(TransactionFiltering.matchesSearch(item(), "coffee salary"))
    assertTrue(TransactionFiltering.matchesSearch(item(), "   "))
  }
  @Test fun `search matches whole amount decimals and goal expense alias`() {
    val whole = item(TransactionType.GOAL).let { it.copy(transaction = it.transaction.copy(amount = 1200)) }
    assertTrue(TransactionFiltering.matchesSearch(whole, "12.00 expense"))
    assertTrue(TransactionFiltering.matchesSearch(whole, "12,00 goal"))
  }
  @Test fun `transfer preview changes both accounts and retains total`() {
    val (from, to) = transferBalanceChanges(mapOf("bank" to 200000L, "cash" to 10000L), null, "bank", "cash", 105000)
    assertEquals(AccountBalanceChange(200000, 95000), from)
    assertEquals(AccountBalanceChange(10000, 115000), to)
    assertEquals(from.before + to.before, from.after + to.after)
  }
  @Test fun `edit preview removes original effect including reversed accounts`() {
    val original = transaction(TransactionType.TRANSFER).copy(amount = 10000)
    val (from, to) = transferBalanceChanges(mapOf("bank" to 90000L, "cash" to 20000L), original, "cash", "bank", 5000)
    assertEquals(AccountBalanceChange(10000, 5000), from)
    assertEquals(AccountBalanceChange(100000, 105000), to)
  }
  @Test fun `preview validates both accounts and overflows without wrapping`() {
    assertThrows(IllegalArgumentException::class.java) { transferBalanceChanges(emptyMap(), null, "cash", "cash", 5000) }
    assertThrows(ArithmeticException::class.java) { transferBalanceChanges(mapOf("cash" to Long.MAX_VALUE), null, "bank", "cash", 1) }
  }
  @Test fun `CSV escapes commas quotes newlines and formulas`() {
    assertEquals("\"a,\"\"b\"\"\nnext\"", TransactionCsv.cell("a,\"b\"\nnext"))
    listOf("=SUM(A1)", "+cmd", "-cmd", "@cmd", "  =cmd", "\tcmd").forEach {
      assertTrue(TransactionCsv.cell(it).startsWith("\"'"))
    }
    assertEquals("\"Coffee\"", TransactionCsv.cell("Coffee"))
  }
  @Test fun `CSV uses exact signed amounts and exports transfer only once`() {
    val output = StringBuilder()
    TransactionCsv.write(listOf(item(), item(TransactionType.INCOME), item(TransactionType.TRANSFER)), output)
    assertTrue(output.contains(",\"EXPENSE\",-1050.25,\"USD\""))
    assertTrue(output.contains(",\"INCOME\",1050.25,\"USD\""))
    assertTrue(output.contains(",\"TRANSFER\",1050.25,\"USD\",\"Bank\",\"Cash\""))
    assertEquals(1, Regex("TRANSFER").findAll(output).count())
    assertTrue(output.contains("\"Client lunch\nsecond line\""))
  }
}
