package com.joeabouserhal.financetracker.data.recurring

import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceCalculatorTest {
  @Test fun `sparse yearly intervals are not truncated to twelve years`() {
    val sparse = schedule().copy(frequency = RecurrenceFrequency.YEARLY, interval = 20, startDate = "2024-02-29")
    assertEquals(LocalDate.of(2044, 2, 29), RecurrenceCalculator.next(sparse, LocalDate.of(2025, 1, 1)))
  }

  @Test fun `weekly selected days retain the start anchored interval`() {
    val weekly = schedule().copy(frequency = RecurrenceFrequency.WEEKLY, interval = 2, startDate = "2026-01-02", weekDays = "1,5")
    assertEquals(listOf("2026-01-02", "2026-01-05", "2026-01-16", "2026-01-19", "2026-01-30"),
      RecurrenceCalculator.occurrences(weekly, LocalDate.of(2026, 1, 31)).map { it.toString() }.toList())
  }

  @Test fun `fifth weekday skips months without one and respects inclusive end`() {
    val fifthMonday = schedule(MonthlyRule.NTH_WEEKDAY, null).copy(startDate = "2026-02-01", nthWeekday = 1, nthOrdinal = 5, endDate = "2026-03-30")
    assertEquals(listOf(LocalDate.of(2026, 3, 30)), RecurrenceCalculator.occurrences(fifthMonday, LocalDate.of(2026, 12, 31)).toList())
  }
  private fun schedule(rule: MonthlyRule = MonthlyRule.DAY_OF_MONTH, day: Int? = 31) =
    RecurringScheduleEntity("id", "owner", "preset", RecurrenceFrequency.MONTHLY, 1, null, rule, day, null, null,
      "2026-01-31", null, 540, false, null, "now", "now")

  @Test fun monthly31UsesLastDayOfShortMonth() {
    assertEquals(LocalDate.of(2026, 2, 28), RecurrenceCalculator.next(schedule(), LocalDate.of(2026, 2, 1)))
  }

  @Test fun lastBusinessDaySkipsWeekend() {
    assertEquals(LocalDate.of(2026, 2, 27), RecurrenceCalculator.next(schedule(MonthlyRule.LAST_BUSINESS_DAY), LocalDate.of(2026, 2, 1)))
  }

  @Test fun leapDayYearlyFallsBackToFeb28() {
    val yearly = schedule().copy(frequency = RecurrenceFrequency.YEARLY, startDate = "2024-02-29")
    assertEquals(LocalDate.of(2025, 2, 28), RecurrenceCalculator.next(yearly, LocalDate.of(2025, 1, 1)))
  }
}
