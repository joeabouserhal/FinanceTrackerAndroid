package com.joeabouserhal.financetracker.data.recurring

import com.joeabouserhal.financetracker.data.local.entities.MonthlyRule
import com.joeabouserhal.financetracker.data.local.entities.RecurringScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pure calendar rules used by the UI preview and the durable worker. */
object RecurrenceCalculator {
  fun occurrences(schedule: RecurringScheduleEntity, through: LocalDate, from: LocalDate = LocalDate.parse(schedule.startDate)): Sequence<LocalDate> = sequence {
    val end = schedule.endDate?.let(LocalDate::parse)?.coerceAtMost(through) ?: through
    var date = next(schedule, from)
    while (date != null && !date.isAfter(end)) {
      yield(date)
      date = next(schedule, date.plusDays(1))
    }
  }

  fun next(schedule: RecurringScheduleEntity, from: LocalDate): LocalDate? {
    require(schedule.interval > 0)
    val start = LocalDate.parse(schedule.startDate)
    val end = schedule.endDate?.let(LocalDate::parse)
    val floor = maxOf(start, from)
    val candidate = when (schedule.frequency) {
      com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.DAILY -> {
        val elapsed = ChronoUnit.DAYS.between(start, floor)
        start.plusDays(((elapsed + schedule.interval - 1) / schedule.interval) * schedule.interval)
      }
      com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.WEEKLY -> {
        val week = ChronoUnit.WEEKS.between(start, floor)
        var date = start.plusWeeks((week / schedule.interval) * schedule.interval)
        // At most one partial cycle plus the next selected week is inspected.
        while (true) {
          val found = (0L..6L).map { date.plusDays(it) }.firstOrNull { it >= floor && matches(schedule, it, start) }
          if (found != null) { date = found; break }
          date = date.plusWeeks(schedule.interval.toLong())
        }
        date
      }
      com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.MONTHLY -> {
        val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(floor))
        var month = YearMonth.from(start).plusMonths((months / schedule.interval) * schedule.interval)
        var found: LocalDate? = null
        // Gregorian weekday patterns repeat within 400 years, even for sparse intervals.
        for (cycle in 0 until 4800) {
          found = (1..month.lengthOfMonth()).asSequence().map { month.atDay(it) }.firstOrNull { it >= floor && matchesMonthRule(schedule, it) }
          if (found != null) break
          month = month.plusMonths(schedule.interval.toLong())
        }
        found ?: return null
      }
      com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.YEARLY -> {
        var year = start.year + ((floor.year - start.year) / schedule.interval) * schedule.interval
        fun dateInYear() = YearMonth.of(year, start.monthValue).atDay(minOf(start.dayOfMonth, YearMonth.of(year, start.monthValue).lengthOfMonth()))
        if (dateInYear() < floor) year += schedule.interval
        dateInYear()
      }
    }
    return candidate.takeIf { end == null || it <= end }
  }

  private fun matches(s: RecurringScheduleEntity, date: LocalDate, start: LocalDate): Boolean = when (s.frequency) {
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.DAILY ->
      ChronoUnit.DAYS.between(start, date) % s.interval == 0L
    com.joeabouserhal.financetracker.data.local.entities.RecurrenceFrequency.WEEKLY -> {
      val weeks = ChronoUnit.WEEKS.between(start, date)
      val days = s.weekDays.orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..7 }.toSet().ifEmpty { setOf(start.dayOfWeek.value) }
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
