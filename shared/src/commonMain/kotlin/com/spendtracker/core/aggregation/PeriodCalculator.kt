package com.spendtracker.core.aggregation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Selects which calendar period the dashboard reports on.
 * Weeks run Monday 00:00 through the next Monday 00:00; months are local
 * calendar months. Both follow the supplied time zone, never UTC, so a device
 * time-zone change can regroup boundary transactions without losing data.
 */
enum class DashboardPeriod {
    WEEK,
    MONTH,
}

/**
 * Half-open epoch-millisecond window `[start, end)` used by every report.
 * Boundaries are exact local midnights converted through the caller's time zone.
 */
data class DateRange(
    val startInclusiveEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
)

/**
 * Pure calendar math for dashboard period boundaries.
 *
 * All functions take an injected instant and [TimeZone] so aggregation is
 * deterministic in tests and correct for the device's current zone. Month
 * arithmetic goes through day-adjusting [DatePeriod] rules (31 January plus one
 * month is 28/29 February), and DST transitions only change the elapsed hours
 * of a boundary, never the calendar dates.
 */
object PeriodCalculator {
    /**
     * The current reporting window containing [nowEpochMillis].
     * Weeks start on Monday; months start on the first of the local month.
     */
    fun currentRange(
        nowEpochMillis: Long,
        timeZone: TimeZone,
        period: DashboardPeriod,
    ): DateRange {
        val today = dateIn(nowEpochMillis, timeZone)
        val start = when (period) {
            DashboardPeriod.WEEK -> today.plus(DatePeriod(days = -(today.dayOfWeek.isoDayNumber - 1)))
            DashboardPeriod.MONTH -> LocalDate(today.year, today.monthNumber, 1)
        }
        return DateRange(startEpoch(start, timeZone), startEpoch(nextStart(start, period), timeZone))
    }

    /**
     * The comparison window for headline change percentages.
     *
     * It covers the same number of elapsed days in the immediately previous
     * period, not the whole previous period: on a Wednesday the comparison is
     * the previous Monday-through-Wednesday. This keeps a partial current period
     * comparable to the partial days that existed at the same point last period.
     * Month comparisons never extend past the end of the previous month.
     */
    fun sameElapsedPreviousRange(
        nowEpochMillis: Long,
        timeZone: TimeZone,
        period: DashboardPeriod,
    ): DateRange {
        val today = dateIn(nowEpochMillis, timeZone)
        val currentStart = when (period) {
            DashboardPeriod.WEEK -> today.plus(DatePeriod(days = -(today.dayOfWeek.isoDayNumber - 1)))
            DashboardPeriod.MONTH -> LocalDate(today.year, today.monthNumber, 1)
        }
        val previousStart = currentStart.plus(DatePeriod(months = if (period == DashboardPeriod.MONTH) -1 else 0))
            .plus(DatePeriod(days = if (period == DashboardPeriod.WEEK) -7 else 0))
        val elapsedDays = when (period) {
            DashboardPeriod.WEEK -> today.dayOfWeek.isoDayNumber - 1
            DashboardPeriod.MONTH -> today.dayOfMonth - 1
        }
        val daysCompared = elapsedDays + 1
        val uncappedEnd = previousStart.plus(DatePeriod(days = daysCompared))
        val previousEnd = nextStart(previousStart, period)
        val end = if (period == DashboardPeriod.MONTH && uncappedEnd > previousEnd) previousEnd else uncappedEnd
        return DateRange(startEpoch(previousStart, timeZone), startEpoch(end, timeZone))
    }

    private fun dateIn(epochMillis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date

    private fun startEpoch(date: LocalDate, timeZone: TimeZone): Long =
        date.atStartOfDayIn(timeZone).toEpochMilliseconds()

    private fun nextStart(start: LocalDate, period: DashboardPeriod): LocalDate = when (period) {
        DashboardPeriod.WEEK -> start.plus(DatePeriod(days = 7))
        DashboardPeriod.MONTH -> start.plus(DatePeriod(months = 1))
    }
}
