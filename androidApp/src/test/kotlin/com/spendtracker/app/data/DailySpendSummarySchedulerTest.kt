package com.spendtracker.app.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies daily scheduling remains anchored to 22:00 Asia/Kolkata. */
class DailySpendSummarySchedulerTest {
    @Test
    fun beforeTenPmSchedulesSameDay() {
        val now = Instant.parse("2026-09-26T12:00:00Z") // 17:30 IST

        assertEquals(
            DailySpendSummaryScheduler.ScheduleSpec(LocalDate.parse("2026-09-26"), 16_200_000L),
            DailySpendSummaryScheduler.scheduleSpec(now),
        )
    }

    @Test
    fun atTenPmSchedulesNextDay() {
        val now = Instant.parse("2026-09-26T16:30:00Z") // 22:00 IST

        assertEquals(
            DailySpendSummaryScheduler.ScheduleSpec(LocalDate.parse("2026-09-27"), 86_400_000L),
            DailySpendSummaryScheduler.scheduleSpec(now),
        )
    }
}
