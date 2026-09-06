package com.spendtracker.app.ui

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import java.time.Instant

/**
 * Central source of synthetic, non-sensitive records for Compose previews.
 * Keeping preview fixtures here avoids copying real inbox data into UI examples
 * and makes populated-screen previews deterministic.
 */
internal object PreviewData {
    val transaction = LedgerTransaction(
        id = "ANDROID_SMS:preview",
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = "preview-transaction",
        sourceFingerprint = "preview",
        sourceReceivedAtEpochMillis = Instant.parse("2026-09-04T07:45:00Z").toEpochMilli(),
        money = Money(amountMinor = 685_00, currency = CurrencyCode.INR),
        direction = TransactionDirection.DEBIT,
        kind = TransactionKind.PURCHASE,
        category = SpendCategory.FOOD_AND_DINING,
        merchant = "Northstar Cafe",
        accountHint = null,
        confidence = 0.90,
        parserVersion = 1,
        reviewReasons = emptySet(),
        includedInSpend = true,
    )

    /** Week of synthetic purchases, one foreign record, one refund, one review row. */
    val dashboardLedger: List<LedgerTransaction> = listOf(
        preview("p1", "2026-09-07T08:10:00Z", 12_400, CurrencyCode.INR, SpendCategory.FOOD_AND_DINING, TransactionKind.PURCHASE),
        preview("p2", "2026-09-07T12:30:00Z", 85_000, CurrencyCode.INR, SpendCategory.GROCERIES, TransactionKind.PURCHASE),
        preview("p3", "2026-09-08T09:05:00Z", 45_500, CurrencyCode.INR, SpendCategory.TRANSPORT, TransactionKind.PURCHASE),
        preview("p4", "2026-09-08T18:40:00Z", 129_900, CurrencyCode.INR, SpendCategory.SHOPPING, TransactionKind.PURCHASE),
        preview("p5", "2026-09-09T07:15:00Z", 30_000, CurrencyCode.INR, SpendCategory.ENTERTAINMENT, TransactionKind.PURCHASE),
        preview("p6", "2026-09-09T10:20:00Z", 59_000, CurrencyCode.USD, SpendCategory.TRAVEL, TransactionKind.PURCHASE),
        preview("p7", "2026-09-10T14:00:00Z", 25_000, CurrencyCode.INR, SpendCategory.FOOD_AND_DINING, TransactionKind.REFUND),
        preview(
            "p8", "2026-09-11T16:45:00Z", 9_900, CurrencyCode.INR, SpendCategory.OTHER,
            TransactionKind.PURCHASE, reviewReasons = setOf(TransactionReviewReason.UNKNOWN_CATEGORY),
            confidence = 0.55,
        ),
    )

    private fun preview(
        id: String,
        timestamp: String,
        amountMinor: Long,
        currency: CurrencyCode,
        category: SpendCategory,
        kind: TransactionKind,
        reviewReasons: Set<TransactionReviewReason> = emptySet(),
        confidence: Double = 0.90,
    ) = LedgerTransaction(
        id = "ANDROID_SMS:$id",
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = id,
        sourceFingerprint = id,
        sourceReceivedAtEpochMillis = Instant.parse(timestamp).toEpochMilli(),
        money = Money(amountMinor, currency),
        direction = TransactionDirection.DEBIT,
        kind = kind,
        category = category,
        merchant = "Synthetic Merchant",
        accountHint = null,
        confidence = confidence,
        parserVersion = 3,
        reviewReasons = reviewReasons,
        includedInSpend = kind == TransactionKind.PURCHASE || kind == TransactionKind.FEE,
    )
}
