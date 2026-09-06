package com.spendtracker.core.aggregation

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies headline inclusion, exact category sums, daily bucketing, counts,
 * comparison percentages, and deterministic ordering without a database.
 */
class ReportAggregatorTest {
    private val utc = TimeZone.UTC
    private val range = DateRange(
        startInclusiveEpochMillis = Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds(),
        endExclusiveEpochMillis = Instant.parse("2026-09-14T00:00:00Z").toEpochMilliseconds(),
    )

    @Test
    fun onlyIncludedInrRecordsReachTheHeadline() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 1_000, CurrencyCode.INR, included = true),
            row("2", "2026-09-07T09:00:00Z", 500, CurrencyCode.INR, included = false),
            row("3", "2026-09-07T10:00:00Z", 900, CurrencyCode.USD, included = true),
            row("4", "2026-09-06T23:59:00Z", 2_000, CurrencyCode.INR, included = true), // outside range
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(1_000, report.includedInrTotalMinor)
        assertEquals(1, report.includedInrCount)
        assertEquals(1, report.foreignCount)
        assertEquals(1, report.excludedCount)
    }

    @Test
    fun categoryTotalsSumExactlyToTheHeadline() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 1_000, CurrencyCode.INR, included = true, SpendCategory.FOOD_AND_DINING),
            row("2", "2026-09-07T09:00:00Z", 250, CurrencyCode.INR, included = true, SpendCategory.TRANSPORT),
            row("3", "2026-09-08T10:00:00Z", 750, CurrencyCode.INR, included = true, SpendCategory.GROCERIES),
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(2_000, report.includedInrTotalMinor)
        assertEquals(2_000, report.categoryBreakdown.sumOf { it.totalMinor })
        assertEquals(3, report.categoryBreakdown.size)
        assertEquals(
            listOf(SpendCategory.FOOD_AND_DINING, SpendCategory.GROCERIES, SpendCategory.TRANSPORT),
            report.categoryBreakdown.map { it.category },
        )
        assertEquals(1_000, report.categoryBreakdown[0].totalMinor)
    }

    @Test
    fun categoryOrderingIsTotalDescendingThenStable() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 300, CurrencyCode.INR, included = true, SpendCategory.TRANSPORT),
            row("2", "2026-09-07T09:00:00Z", 100, CurrencyCode.INR, included = true, SpendCategory.FOOD_AND_DINING),
            row("3", "2026-09-07T10:00:00Z", 100, CurrencyCode.INR, included = true, SpendCategory.TRANSPORT),
            row("4", "2026-09-07T11:00:00Z", 100, CurrencyCode.INR, included = true, SpendCategory.GROCERIES),
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(
            listOf(SpendCategory.TRANSPORT, SpendCategory.FOOD_AND_DINING, SpendCategory.GROCERIES),
            report.categoryBreakdown.map { it.category },
        )
        assertEquals(400, report.categoryBreakdown[0].totalMinor)
        assertEquals(2, report.categoryBreakdown[0].transactionCount)
    }

    @Test
    fun dailySeriesBucketsByLocalDayAndKeepsZeroDays() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 1_000, CurrencyCode.INR, included = true),
            row("2", "2026-09-07T20:00:00Z", 500, CurrencyCode.INR, included = true),
            row("3", "2026-09-09T06:00:00Z", 700, CurrencyCode.INR, included = true),
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(7, report.dailySeries.size)
        assertEquals(1_500, report.dailySeries[0].totalMinor) // Monday
        assertEquals(0, report.dailySeries[1].totalMinor) // Tuesday
        assertEquals(700, report.dailySeries[2].totalMinor) // Wednesday
        assertEquals(Instant.parse("2026-09-07T00:00:00Z").toEpochMilliseconds(), report.dailySeries[0].dayStartEpochMillis)
    }

    @Test
    fun reviewCountCoversForeignAndExcludedRecordsToo() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 1_000, CurrencyCode.INR, included = true)
                .copy(
                    reviewReasons = setOf(TransactionReviewReason.CONFLICTING_AMOUNTS),
                    confidence = 0.35,
                ),
            row("2", "2026-09-07T09:00:00Z", 900, CurrencyCode.USD, included = false),
            row("3", "2026-09-07T10:00:00Z", 50, CurrencyCode.INR, included = false),
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(1, report.reviewCount)
        assertEquals(1_000, report.includedInrTotalMinor)
    }

    @Test
    fun comparisonPercentageUsesNearestIntegerRounding() {
        assertEquals(20, ReportAggregator.compare(120, 100).deltaPercent)
        assertEquals(-20, ReportAggregator.compare(80, 100).deltaPercent)
        assertEquals(5, ReportAggregator.compare(105, 100).deltaPercent)
        assertEquals(6, ReportAggregator.compare(106, 100).deltaPercent)
        assertEquals(0, ReportAggregator.compare(100, 100).deltaPercent)
        assertNull(ReportAggregator.compare(50, 0).deltaPercent)
        assertEquals(100, ReportAggregator.compare(200, 100).deltaPercent)
    }

    @Test
    fun foreignOnlyExcludedAndIncludedCountsAreIndependent() {
        val rows = listOf(
            row("1", "2026-09-07T08:00:00Z", 100, CurrencyCode.USD, included = false),
            row("2", "2026-09-07T09:00:00Z", 100, CurrencyCode.INR, included = false),
            row("3", "2026-09-07T10:00:00Z", 100, CurrencyCode.INR, included = true),
        )
        val report = ReportAggregator.compute(rows, range, DashboardPeriod.WEEK, utc)
        assertEquals(1, report.foreignCount)
        assertEquals(1, report.excludedCount)
        assertEquals(1, report.includedInrCount)
    }

    private fun row(
        id: String,
        timestamp: String,
        amountMinor: Long,
        currency: CurrencyCode,
        included: Boolean,
        category: SpendCategory = SpendCategory.FOOD_AND_DINING,
    ) = LedgerTransaction(
        id = id,
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = id,
        sourceFingerprint = id,
        sourceReceivedAtEpochMillis = Instant.parse(timestamp).toEpochMilliseconds(),
        money = Money(amountMinor, currency),
        direction = TransactionDirection.DEBIT,
        kind = TransactionKind.PURCHASE,
        category = category,
        merchant = "Synthetic Merchant",
        accountHint = null,
        confidence = 0.90,
        parserVersion = 3,
        reviewReasons = emptySet(),
        includedInSpend = included,
    )
}
