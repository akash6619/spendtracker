package com.spendtracker.app.ui.settings

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Simple re-parse windows offered in settings. Each period means "from the
 * start of that calendar period until now", so the dialog never needs a range
 * end picker. Weeks start on Monday.
 */
internal enum class ReparsePeriod {
    CURRENT_MONTH,
    LAST_MONTH,
    CURRENT_WEEK,
    LAST_WEEK,
}

/**
 * Returns the local start-of-day epoch millis for [period] relative to [today].
 * The result is clamped by the caller to the import policy window.
 */
internal fun reparsePeriodStart(
    period: ReparsePeriod,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val startDate = when (period) {
        ReparsePeriod.CURRENT_MONTH -> today.withDayOfMonth(1)
        ReparsePeriod.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1)
        ReparsePeriod.CURRENT_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        ReparsePeriod.LAST_WEEK -> today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1)
    }
    return startDate.atStartOfDay(zone).toInstant().toEpochMilli()
}
