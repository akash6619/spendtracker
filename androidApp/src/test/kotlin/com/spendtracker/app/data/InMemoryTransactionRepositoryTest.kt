package com.spendtracker.app.data

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Verifies that the debug/test repository mirrors production categorization semantics.
 *
 * These tests guard normalized merchant-rule matching and review-state resolution so
 * ViewModel tests and demo mode do not behave differently from the Room repository.
 */
class InMemoryTransactionRepositoryTest {
    @Test
    fun merchantRuleMatchesNormalizedCandidateAndResolvesCategoryReview() = runTest {
        val repository = InMemoryTransactionRepository()
        repository.saveMerchantRule("The Corner Cafe", SpendCategory.FOOD_AND_DINING)

        repository.upsert(
            listOf(
                candidate(
                    merchant = "  THE   CORNER CAFE!!! ",
                    reviewReasons = setOf(TransactionReviewReason.UNKNOWN_CATEGORY),
                ),
            ),
        )

        val transaction = repository.observeTransactions().first().single()
        assertEquals("THE CORNER CAFE", transaction.merchant)
        assertEquals(SpendCategory.FOOD_AND_DINING, transaction.category)
        assertTrue(transaction.reviewReasons.isEmpty())
        assertEquals(0.9, transaction.confidence)
    }

    private fun candidate(
        merchant: String,
        reviewReasons: Set<TransactionReviewReason>,
    ) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = "provider-1",
        sourceFingerprint = "fingerprint-1",
        transaction = ParsedTransaction(
            sourceId = "provider-1",
            sourceReceivedAtEpochMillis = 1_000L,
            money = Money(1_000L, CurrencyCode.INR),
            direction = TransactionDirection.DEBIT,
            kind = TransactionKind.PURCHASE,
            category = SpendCategory.OTHER,
            merchant = merchant,
            accountHint = null,
            confidence = 0.6,
            parserVersion = 3,
            reviewReasons = reviewReasons,
        ),
    )
}
