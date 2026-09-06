package com.spendtracker.core.aggregation

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.needsReview
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * One category's included INR spend inside a report window.
 * Amounts stay in exact minor units and count supports explainable rows.
 */
data class CategorySpend(
    val category: SpendCategory,
    val totalMinor: Long,
    val transactionCount: Int,
)

/**
 * One calendar day's included INR spend starting at local midnight.
 * The epoch value marks the local day start in the report's time zone.
 */
data class DailySpend(
    val dayStartEpochMillis: Long,
    val totalMinor: Long,
)

/**
 * Complete dashboard facts for one calendar window.
 *
 * The headline is included INR only. Category and daily series use the same
 * records, so category totals sum exactly to the headline. Foreign, excluded,
 * and review counts describe visible records that never touch the headline.
 */
data class PeriodReport(
    val period: DashboardPeriod,
    val range: DateRange,
    val includedInrTotalMinor: Long,
    val includedInrCount: Int,
    val categoryBreakdown: List<CategorySpend>,
    val foreignCount: Int,
    val excludedCount: Int,
    val reviewCount: Int,
    val dailySeries: List<DailySpend>,
)

/**
 * Headline comparison between the current window and the same elapsed days of
 * the previous period. [deltaPercent] is null when the previous total is zero,
 * because no meaningful percentage exists for an empty baseline.
 */
data class PeriodComparison(
    val currentTotalMinor: Long,
    val previousTotalMinor: Long,
    val deltaMinor: Long,
    val deltaPercent: Int?,
)

/**
 * Derives every dashboard number from the observed ledger and one window.
 *
 * All rules live here so UI and ViewModel never mix currencies or overrides:
 * only effectively included INR records contribute to totals, category sums
 * match the headline exactly, and non-INR or excluded records only appear in
 * counts. Percentages are display-only and never feed monetary math.
 */
object ReportAggregator {
    /**
     * Computes the report for [range] using the stored single-value fields.
     * [timeZone] only groups timestamps into local calendar days for the series.
     */
    fun compute(
        transactions: List<LedgerTransaction>,
        range: DateRange,
        period: DashboardPeriod,
        timeZone: TimeZone,
    ): PeriodReport {
        val inRange = transactions.filter {
            it.sourceReceivedAtEpochMillis in range.startInclusiveEpochMillis until range.endExclusiveEpochMillis
        }
        var includedTotal = 0L
        var includedCount = 0
        val categoryTotals = mutableMapOf<SpendCategory, Long>()
        val categoryCounts = mutableMapOf<SpendCategory, Int>()
        val dailyTotals = mutableMapOf<LocalDate, Long>()
        var foreignCount = 0
        var excludedCount = 0
        var reviewCount = 0

        inRange.forEach { row ->
            val currency = row.money.currency
            when {
                currency != CurrencyCode.INR -> foreignCount += 1
                !row.includedInSpend -> excludedCount += 1
                else -> {
                    includedTotal += row.money.amountMinor
                    includedCount += 1
                    categoryTotals[row.category] = (categoryTotals[row.category] ?: 0L) + row.money.amountMinor
                    categoryCounts[row.category] = (categoryCounts[row.category] ?: 0) + 1
                    val day = rowDay(row, timeZone)
                    dailyTotals[day] = (dailyTotals[day] ?: 0L) + row.money.amountMinor
                }
            }
            if (row.needsReview) reviewCount += 1
        }

        val breakdown = categoryTotals.map { (category, total) ->
            CategorySpend(category, total, categoryCounts.getValue(category))
        }.sortedWith(
            compareByDescending<CategorySpend> { it.totalMinor }.thenBy { it.category.ordinal },
        )

        val startDate = Instant.fromEpochMilliseconds(range.startInclusiveEpochMillis)
            .toLocalDateTime(timeZone).date
        val endDate = Instant.fromEpochMilliseconds(range.endExclusiveEpochMillis)
            .toLocalDateTime(timeZone).date
        val dailySeries = buildList {
            var day = startDate
            while (day < endDate) {
                add(
                    DailySpend(
                        dayStartEpochMillis = day.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                        totalMinor = dailyTotals[day] ?: 0L,
                    ),
                )
                day = day.plus(DatePeriod(days = 1))
            }
        }

        return PeriodReport(
            period = period,
            range = range,
            includedInrTotalMinor = includedTotal,
            includedInrCount = includedCount,
            categoryBreakdown = breakdown,
            foreignCount = foreignCount,
            excludedCount = excludedCount,
            reviewCount = reviewCount,
            dailySeries = dailySeries,
        )
    }

    /** Compares two exact totals; percentage uses nearest-integer rounding. */
    fun compare(currentTotalMinor: Long, previousTotalMinor: Long): PeriodComparison {
        val delta = currentTotalMinor - previousTotalMinor
        val percent = if (previousTotalMinor == 0L) {
            null
        } else {
            (delta * 100.0 / previousTotalMinor).roundToInt()
        }
        return PeriodComparison(currentTotalMinor, previousTotalMinor, delta, percent)
    }

    private fun rowDay(row: LedgerTransaction, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(row.sourceReceivedAtEpochMillis)
            .toLocalDateTime(timeZone).date
}
