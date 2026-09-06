package com.spendtracker.core.aggregation

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the Monday-start week, local calendar month, and same-elapsed-day
 * comparison windows against day, month, year, leap, DST, and time-zone edges.
 */
class PeriodCalculatorTest {
    private val utc = TimeZone.UTC

    @Test
    fun weekStartsMondayAtLocalMidnight() {
        val now = Instant.parse("2026-09-09T14:30:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.WEEK)
        assertEquals(Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2026-09-14T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun sundayBelongsToTheWeekThatStartedSixDaysEarlier() {
        val now = Instant.parse("2026-09-13T23:59:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.WEEK)
        assertEquals(Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
    }

    @Test
    fun mondayMidnightIsInclusiveBoundary() {
        val now = Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.WEEK)
        assertEquals(now, range.startInclusiveEpochMillis)
    }

    @Test
    fun monthUsesLocalCalendarBoundaries() {
        val now = Instant.parse("2026-08-15T10:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun monthSpansYearBoundary() {
        val now = Instant.parse("2026-12-31T20:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2027-01-01T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun leapFebruaryHasTwentyNineDays() {
        val now = Instant.parse("2028-02-29T12:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2028-02-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2028-03-01T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun nonLeapFebruaryHasTwentyEightDays() {
        val now = Instant.parse("2027-02-28T12:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.currentRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2027-02-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2027-03-01T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun monthMarch31PreviousComparisonStartsFebruary1() {
        val now = Instant.parse("2027-03-31T12:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.sameElapsedPreviousRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2027-02-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        // March 31 elapsed 30 days plus today; February has only 28 days, so the
        // comparison is capped at the previous month's end.
        assertEquals(Instant.parse("2027-03-01T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun weekComparisonUsesSameElapsedDays() {
        val now = Instant.parse("2026-09-09T12:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.sameElapsedPreviousRange(now, utc, DashboardPeriod.WEEK)
        assertEquals(Instant.parse("2026-08-31T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        // Wednesday is the third day, so the comparison is Monday through Wednesday.
        assertEquals(Instant.parse("2026-09-03T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun monthComparisonOnDayOneComparesOneDay() {
        val now = Instant.parse("2026-09-01T12:00:00Z").toEpochMilliseconds()
        val range = PeriodCalculator.sameElapsedPreviousRange(now, utc, DashboardPeriod.MONTH)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z").toEpochMilliseconds(), range.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2026-08-02T00:00:00Z").toEpochMilliseconds(), range.endExclusiveEpochMillis)
    }

    @Test
    fun kolkataBoundariesDifferFromUtcForSameInstant() {
        val kolkata = TimeZone.of("Asia/Kolkata")
        val now = Instant.parse("2026-09-08T21:00:00Z").toEpochMilliseconds() // 02:30 on 9 Sep in IST
        val utcRange = PeriodCalculator.currentRange(now, utc, DashboardPeriod.WEEK)
        val kolkataRange = PeriodCalculator.currentRange(now, kolkata, DashboardPeriod.WEEK)
        // Both are their own local Mondays, which differ by the zone offset.
        assertEquals(Instant.parse("2026-09-06T18:30:00Z").toEpochMilliseconds(), kolkataRange.startInclusiveEpochMillis)
        assertEquals(Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds(), utcRange.startInclusiveEpochMillis)
        assert(kolkataRange.startInclusiveEpochMillis != utcRange.startInclusiveEpochMillis)
    }

    @Test
    fun dstTransitionWeekKeepsCalendarDatesAndShrinksElapsedHours() {
        val newYork = TimeZone.of("America/New_York")
        // Saturday before the 2026-03-08 spring-forward transition.
        val now = Instant.parse("2026-03-07T17:00:00Z").toEpochMilliseconds() // noon EST
        val range = PeriodCalculator.currentRange(now, newYork, DashboardPeriod.WEEK)
        val startDate = Instant.fromEpochMilliseconds(range.startInclusiveEpochMillis)
            .toLocalDateTime(newYork).date
        val endDate = Instant.fromEpochMilliseconds(range.endExclusiveEpochMillis)
            .toLocalDateTime(newYork).date
        assertEquals("2026-03-02", startDate.toString())
        assertEquals("2026-03-09", endDate.toString())
        // Seven local calendar days crossed the spring-forward gap: 167 hours.
        assertEquals(167L * 3_600_000L, range.endExclusiveEpochMillis - range.startInclusiveEpochMillis)
    }
}
