package com.joeabouserhal.financetracker.data.recurring

import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pure calendar rules used by the UI preview and the durable worker. */
object RecurrenceCalculator {
  fun occurrences(schedule: RecurringScheduleEntity, through: LocalDate): Sequence<LocalDate> = sequence {
    val start = LocalDate.parse(schedule.startDate)
    val end = schedule.endDate?.let(LocalDate::parse)?.coerceAtMost(through) ?: through
    var date = start
    while (!date.isAfter(end)) {
      if (matches(schedule, date, start)) yield(date)
      date = date.plusDays(1)
    }
  }

  fun next(schedule: RecurringScheduleEntity, from: LocalDate): LocalDate? {
    val end = schedule.endDate?.let(LocalDate::parse)
    var date = maxOf(LocalDate.parse(schedule.startDate), from)
    repeat(366 * 12) {
      if (end != null && date.isAfter(end)) return null
      if (matches(schedule, date, LocalDate.parse(schedule.startDate))) return date
      date = date.plusDays(1)
    }
    return null
  }

  private fun matches(s: RecurringScheduleEntity, date: LocalDate, start: LocalDate): Boolean = when (s.frequency) {
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.DAILY ->
      ChronoUnit.DAYS.between(start, date) % s.interval == 0L
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.WEEKLY -> {
      val weeks = ChronoUnit.WEEKS.between(start, date)
      val days = s.weekDays.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet().ifEmpty { setOf(start.dayOfWeek.value) }
      weeks >= 0 && weeks % s.interval == 0L && date.dayOfWeek.value in days
    }
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.MONTHLY -> {
      val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date))
      months >= 0 && months % s.interval == 0L && matchesMonthRule(s, date)
    }
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.YEARLY ->
      date.year >= start.year && (date.year - start.year) % s.interval == 0 && date ==
        LocalDate.of(date.year, start.monthValue, minOf(start.dayOfMonth, YearMonth.of(date.year, start.monthValue).lengthOfMonth()))
  }

  private fun matchesMonthRule(s: RecurringScheduleEntity, date: LocalDate): Boolean = when (s.monthlyRule) {
    MonthlyRule.DAY_OF_MONTH -> date.dayOfMonth == minOf(s.dayOfMonth ?: 1, date.lengthOfMonth())
    MonthlyRule.LAST_DAY -> date.dayOfMonth == date.lengthOfMonth()
    MonthlyRule.LAST_BUSINESS_DAY -> date == lastBusinessDay(YearMonth.from(date))
    MonthlyRule.NTH_WEEKDAY -> date.dayOfWeek.value == (s.nthWeekday ?: DayOfWeek.MONDAY.value) &&
      ((date.dayOfMonth - 1) / 7 + 1) == (s.nthOrdinal ?: 1)
  }

  private fun lastBusinessDay(month: YearMonth): LocalDate {
    var day = month.atEndOfMonth()
    while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.minusDays(1)
    return day
  }
}
