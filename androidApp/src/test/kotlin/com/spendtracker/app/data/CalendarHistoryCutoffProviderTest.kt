package com.spendtracker.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Locks down calendar-month subtraction at dates where duration arithmetic fails.
 * Injected instants and zones prove that the provider preserves local wall time
 * across short months and leap years.
 */
class CalendarHistoryCutoffProviderTest {
    @Test
    fun subtractsThreeLocalCalendarMonthsAtMonthEnd() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 5, 31, 18, 45, 0, 0, zone).toInstant()

        val cutoff = CalendarHistoryCutoffProvider(now = { now }, zoneId = { zone }).cutoffEpochMillis()

        assertEquals(
            ZonedDateTime.of(2026, 2, 28, 18, 45, 0, 0, zone).toInstant().toEpochMilli(),
            cutoff,
        )
    }

    @Test
    fun respectsLeapDayAndInjectedZone() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2024-05-31T16:00:00Z")

        val cutoff = CalendarHistoryCutoffProvider(now = { now }, zoneId = { zone }).cutoffEpochMillis()

        assertEquals(
            ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, zone).toInstant().toEpochMilli(),
            cutoff,
        )
    }
}
