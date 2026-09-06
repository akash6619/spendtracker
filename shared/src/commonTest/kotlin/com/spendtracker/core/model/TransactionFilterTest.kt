package com.spendtracker.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the foreign-currency dimension, review reasoning, and interaction with
 * the inclusion filter.
 */
class TransactionFilterTest {

    @Test
    fun foreignOnlyMatchesEveryNonInrCurrency() {
        val filter = TransactionFilter(foreignOnly = true)
        assertTrue(filter.matches(row(CurrencyCode.USD)))
        assertTrue(filter.matches(row(CurrencyCode.JPY)))
        assertFalse(filter.matches(row(CurrencyCode.INR)))
    }

    @Test
    fun foreignOnlyCombinesWithInclusionAndReview() {
        val filter = TransactionFilter(foreignOnly = true, included = false, needsReview = true)
        assertTrue(filter.matches(row(CurrencyCode.USD, review = true)))
        assertFalse(filter.matches(row(CurrencyCode.USD)))
        assertFalse(filter.matches(row(CurrencyCode.INR)))
    }

    @Test
    fun explicitCurrencyStillWinsOverForeignOnly() {
        assertTrue(TransactionFilter(currency = CurrencyCode.INR).matches(row(CurrencyCode.INR)))
        assertFalse(TransactionFilter(currency = CurrencyCode.INR).matches(row(CurrencyCode.USD)))
        // A concrete currency with foreignOnly keeps the concrete match.
        assertTrue(TransactionFilter(currency = CurrencyCode.USD, foreignOnly = true).matches(row(CurrencyCode.USD)))
        assertFalse(TransactionFilter(currency = CurrencyCode.USD, foreignOnly = true).matches(row(CurrencyCode.INR)))
    }

    @Test
    fun missingMerchantAloneIsNotAReviewConcern() {
        val row = row(CurrencyCode.INR).copy(
            reviewReasons = setOf(TransactionReviewReason.MISSING_MERCHANT),
            confidence = 0.60,
        )
        assertFalse(row.needsReview)
    }

    @Test
    fun unknownKindRequiresItsReviewReasonNow() {
        val noReason = row(CurrencyCode.INR).copy(kind = TransactionKind.UNKNOWN, confidence = 0.90)
        assertFalse(noReason.needsReview)

        val withReason = noReason.copy(
            reviewReasons = setOf(TransactionReviewReason.UNKNOWN_KIND),
            confidence = 0.60,
        )
        assertTrue(withReason.needsReview)
    }

    @Test
    fun userConfirmationClosesReview() {
        val row = row(CurrencyCode.INR).copy(
            reviewReasons = setOf(TransactionReviewReason.CONFLICTING_AMOUNTS),
            confidence = 0.35,
        )
        assertTrue(row.needsReview)
        val confirmed = row.copy(
            category = SpendCategory.SHOPPING,
            reviewReasons = emptySet(),
            confidence = 0.90,
        )
        assertFalse(confirmed.needsReview)
    }

    private fun row(currency: CurrencyCode, review: Boolean = false) = LedgerTransaction(
        id = "id-$currency",
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = "provider-$currency",
        sourceFingerprint = "fingerprint-$currency",
        sourceReceivedAtEpochMillis = 1_788_457_600_000,
        money = Money(100, currency),
        direction = TransactionDirection.DEBIT,
        kind = TransactionKind.PURCHASE,
        category = SpendCategory.FOOD_AND_DINING,
        merchant = "Synthetic Merchant",
        accountHint = null,
        confidence = 0.90,
        parserVersion = 3,
        reviewReasons = if (review) setOf(TransactionReviewReason.UNKNOWN_CATEGORY) else emptySet(),
        includedInSpend = false,
    )
}
