package com.joeabouserhal.financetracker.ui.components

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarLayoutTest {
  @Test fun `every calendar row always has seven equal cells`() {
    for (year in 2023..2028) for (month in 1..12) for (firstDay in DayOfWeek.entries) {
      val date = YearMonth.of(year, month)
      val weeks = calendarWeeks(date, firstDay)
      assertTrue(weeks.all { it.size == 7 })
      assertEquals((1..date.lengthOfMonth()).map(date::atDay), weeks.flatten().filterNotNull())
      weeks.forEach { week ->
        week.forEachIndexed { index, day ->
          if (day != null) assertEquals(firstDay.plus(index.toLong()), day.dayOfWeek)
        }
      }
    }
  }

  @Test fun `last column stays present in the reported October calendar`() {
    val weeks = calendarWeeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
    assertEquals(24, weeks[3][6]?.dayOfMonth)
    assertEquals(31, weeks[4][6]?.dayOfMonth)
  }

  @Test fun `leap day and trailing blank columns are retained`() {
    val weeks = calendarWeeks(YearMonth.of(2024, 2), DayOfWeek.MONDAY)
    assertEquals(29, weeks.last()[3]?.dayOfMonth)
    assertTrue(weeks.last().takeLast(3).all { it == null })
  }
}
