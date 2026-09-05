package com.spendtracker.app.data

import com.spendtracker.core.importing.HistoryCutoffProvider
import com.spendtracker.core.importing.ImportPolicy
import java.time.Instant
import java.time.ZoneId

/**
 * Calculates the initial import boundary using device-local calendar months.
 * The clock and zone are injectable so month-end, leap-year, and time-zone
 * behavior can be tested without changing system time.
 */
class CalendarHistoryCutoffProvider(
    private val now: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val historyMonths: Int = ImportPolicy.DEFAULT_HISTORY_MONTHS,
) : HistoryCutoffProvider {
    init {
        require(historyMonths > 0) { "History window must be positive" }
    }

    override fun cutoffEpochMillis(): Long = now()
        .atZone(zoneId())
        .minusMonths(historyMonths.toLong())
        .toInstant()
        .toEpochMilli()
}
