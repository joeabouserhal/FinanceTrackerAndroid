package com.joeabouserhal.financetracker.ui.transactions

import com.joeabouserhal.financetracker.data.local.entities.TransactionEntity
import com.joeabouserhal.financetracker.data.local.entities.TransactionType
import com.joeabouserhal.financetracker.data.repositories.TransactionListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFilteringTest {
  private fun item(
    id: String,
    type: TransactionType,
    amount: Long,
    date: String,
    categoryId: String = "cat-1",
    accountId: String = "acc-1",
    currencyId: String = "cur-1",
    title: String? = null,
    notes: String? = null,
    categoryName: String = "Other",
    createdAt: String = "2026-01-01T00:00:00Z",
  ) = TransactionListItem(
    transaction =
      TransactionEntity(
        id = id,
        ownerId = "guest",
        type = type,
        amount = amount,
        currencyId = currencyId,
        categoryId = categoryId,
        accountId = accountId,
        date = date,
        title = title,
        notes = notes,
        presetId = null,
        goalId = null,
        createdAt = createdAt,
        updatedAt = createdAt,
      ),
    categoryName = categoryName,
    categoryColor = "#000000",
    currencySymbol = "$",
    accountName = "Cash",
  )

  private val items =
    listOf(
      item("t1", TransactionType.INCOME, 100, "2026-08-01", categoryId = "cat-a", accountId = "acc-a", title = "Salary"),
      item("t2", TransactionType.EXPENSE, 300, "2026-08-02", categoryId = "cat-b", accountId = "acc-b", title = "Rent"),
      item("t3", TransactionType.EXPENSE, 200, "2026-08-03", categoryId = "cat-a", accountId = "acc-a", notes = "coffee run", currencyId = "cur-2"),
    )

  @Test
  fun `type filter`() {
    val result = TransactionFiltering.apply(items, TransactionFilterState(type = TypeFilter.INCOME))
    assertEquals(listOf("t1"), result.map { it.transaction.id })
  }

  @Test
  fun `expense type filter also matches goal withdrawals`() {
    val goalItem = item("t4", TransactionType.GOAL, 400, "2026-08-04", categoryId = "cat-b", accountId = "acc-b", title = "Trip fund")
    val withGoal = items + goalItem
    assertEquals(
      setOf("t4", "t3", "t2"),
      TransactionFiltering.apply(withGoal, TransactionFilterState(type = TypeFilter.EXPENSE)).map { it.transaction.id }.toSet(),
    )
    assertEquals(
      listOf("t1"),
      TransactionFiltering.apply(withGoal, TransactionFilterState(type = TypeFilter.INCOME)).map { it.transaction.id },
    )
  }

  @Test
  fun `category account and currency filters`() {
    assertEquals(
      listOf("t3", "t1"),
      TransactionFiltering.apply(items, TransactionFilterState(categoryIds = setOf("cat-a"))).map { it.transaction.id },
    )
    assertEquals(
      listOf("t2"),
      TransactionFiltering.apply(items, TransactionFilterState(accountIds = setOf("acc-b"))).map { it.transaction.id },
    )
    assertEquals(
      listOf("t3"),
      TransactionFiltering.apply(items, TransactionFilterState(currencyIds = setOf("cur-2"))).map { it.transaction.id },
    )
  }

  @Test
  fun `search covers title notes and category name`() {
    assertEquals(listOf("t1"), TransactionFiltering.apply(items, TransactionFilterState(search = "salar")).map { it.transaction.id })
    assertEquals(listOf("t3"), TransactionFiltering.apply(items, TransactionFilterState(search = "COFFEE")).map { it.transaction.id })
    assertEquals(listOf("t2"), TransactionFiltering.apply(items, TransactionFilterState(search = "rent")).map { it.transaction.id })
  }

  @Test
  fun `date range is inclusive`() {
    val result = TransactionFiltering.apply(items, TransactionFilterState(dateFrom = "2026-08-02", dateTo = "2026-08-03"))
    assertEquals(listOf("t3", "t2"), result.map { it.transaction.id })
  }

  @Test
  fun `sort orders`() {
    assertEquals(listOf("t3", "t2", "t1"), TransactionFiltering.apply(items, TransactionFilterState(sort = SortOrder.NEWEST)).map { it.transaction.id })
    assertEquals(listOf("t1", "t2", "t3"), TransactionFiltering.apply(items, TransactionFilterState(sort = SortOrder.OLDEST)).map { it.transaction.id })
    assertEquals(listOf("t2", "t3", "t1"), TransactionFiltering.apply(items, TransactionFilterState(sort = SortOrder.LARGEST)).map { it.transaction.id })
    assertEquals(listOf("t1", "t3", "t2"), TransactionFiltering.apply(items, TransactionFilterState(sort = SortOrder.SMALLEST)).map { it.transaction.id })
  }

  @Test
  fun `groupByDate preserves sorted order`() {
    val grouped = TransactionFiltering.groupByDate(TransactionFiltering.apply(items, TransactionFilterState(sort = SortOrder.NEWEST)))
    assertEquals(listOf("2026-08-03", "2026-08-02", "2026-08-01"), grouped.map { it.first })
    assertEquals(listOf("t3"), grouped[0].second.map { it.transaction.id })
  }

  @Test
  fun `activeCount and isDefault`() {
    assertEquals(0, TransactionFiltering.activeCount(TransactionFilterState()))
    assertTrue(TransactionFilterState().isDefault())
    assertEquals(
      4,
      TransactionFiltering.activeCount(
        TransactionFilterState(type = TypeFilter.EXPENSE, categoryIds = setOf("c"), sort = SortOrder.LARGEST, search = "x"),
      ),
    )
    assertEquals(1, TransactionFiltering.activeCount(TransactionFilterState(dateFrom = "2026-01-01", dateTo = "2026-01-31")))
  }
}
