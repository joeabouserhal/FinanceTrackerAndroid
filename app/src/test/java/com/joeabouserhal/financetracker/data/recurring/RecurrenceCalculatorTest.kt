package com.joeabouserhal.financetracker.data.recurring

import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceCalculatorTest {
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
