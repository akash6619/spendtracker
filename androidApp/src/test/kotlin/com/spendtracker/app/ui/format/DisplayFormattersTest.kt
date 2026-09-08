package com.spendtracker.app.ui.format

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import org.junit.Test

/** Verifies compact, device-local date labels for dashboard report ranges. */
class DisplayFormattersTest {
    private val zone = ZoneId.systemDefault()
    private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    @Test
    fun singleDayRangeShowsTheDateOnce() {
        val start = LocalDate.of(2026, 9, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 9, 9).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(formatter.format(LocalDate.of(2026, 9, 8)), formatRange(start, end))
    }

    @Test
    fun multiDayRangeShowsInclusiveFirstAndLastDates() {
        val start = LocalDate.of(2026, 9, 7).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 9, 14).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals(
            "${formatter.format(LocalDate.of(2026, 9, 7))} – ${formatter.format(LocalDate.of(2026, 9, 13))}",
            formatRange(start, end),
        )
    }
}
