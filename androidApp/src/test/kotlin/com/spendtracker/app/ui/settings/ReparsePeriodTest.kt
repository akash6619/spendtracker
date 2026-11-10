package com.spendtracker.app.ui.settings

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies the simple calendar starts used by the re-parse dialog. */
class ReparsePeriodTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val tuesday = LocalDate.of(2026, 11, 10)

    @Test
    fun periodStartsUseCalendarBoundaries() {
        assertEquals(startOf(2026, 11, 1), start(ReparsePeriod.CURRENT_MONTH))
        assertEquals(startOf(2026, 10, 1), start(ReparsePeriod.LAST_MONTH))
        assertEquals(startOf(2026, 11, 9), start(ReparsePeriod.CURRENT_WEEK))
        assertEquals(startOf(2026, 11, 2), start(ReparsePeriod.LAST_WEEK))
    }

    @Test
    fun previousMonthCrossesYearAndMonthBoundaries() {
        val january = LocalDate.of(2026, 1, 5)
        assertEquals(
            startOf(2025, 12, 1),
            reparsePeriodStart(ReparsePeriod.LAST_MONTH, january, zone),
        )
        val march = LocalDate.of(2026, 3, 15)
        assertEquals(
            startOf(2026, 2, 1),
            reparsePeriodStart(ReparsePeriod.LAST_MONTH, march, zone),
        )
    }

    @Test
    fun weeksStartOnMonday() {
        // Sunday belongs to the week that began the preceding Monday.
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(
            startOf(2026, 11, 9),
            reparsePeriodStart(ReparsePeriod.CURRENT_WEEK, sunday, zone),
        )
        assertEquals(
            startOf(2026, 11, 2),
            reparsePeriodStart(ReparsePeriod.LAST_WEEK, sunday, zone),
        )
    }

    private fun start(period: ReparsePeriod) = reparsePeriodStart(period, tuesday, zone)

    private fun startOf(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()
}
