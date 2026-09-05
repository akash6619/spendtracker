package com.spendtracker.app.di

import com.spendtracker.app.data.InMemoryTransactionRepository
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.repository.TransactionRepository
import java.time.Instant

/**
 * Debug-build dependency provider for safe UI exploration.
 * It creates an in-memory repository seeded with synthetic transactions so the
 * app can demonstrate populated screens without SMS permission or private data.
 */
object BuildVariantDependencies {
    fun createDemoRepository(): TransactionRepository =
        InMemoryTransactionRepository(
            listOf(
                demoTransaction(
                    sourceId = "demo-cafe",
                    instant = "2026-09-04T07:45:00Z",
                    amountMinor = 685_00,
                    merchant = "Northstar Cafe",
                    category = SpendCategory.FOOD_AND_DINING,
                ),
                demoTransaction(
                    sourceId = "demo-grocery",
                    instant = "2026-09-03T13:20:00Z",
                    amountMinor = 2_430_50,
                    merchant = "Green Basket",
                    category = SpendCategory.GROCERIES,
                ),
                demoTransaction(
                    sourceId = "demo-transit",
                    instant = "2026-09-02T03:50:00Z",
                    amountMinor = 410_00,
                    merchant = "City Metro",
                    category = SpendCategory.TRANSPORT,
                ),
                demoTransaction(
                    sourceId = "demo-transfer",
                    instant = "2026-09-01T10:10:00Z",
                    amountMinor = 5_000_00,
                    merchant = "Sample Recipient",
                    category = SpendCategory.OTHER,
                    kind = TransactionKind.TRANSFER,
                ),
                demoTransaction(
                    sourceId = "demo-foreign",
                    instant = "2026-08-31T16:00:00Z",
                    amountMinor = 18_00,
                    currency = CurrencyCode.USD,
                    merchant = "Example Software",
                    category = SpendCategory.SUBSCRIPTIONS,
                ),
            ),
        )

    private fun demoTransaction(
        sourceId: String,
        instant: String,
        amountMinor: Long,
        merchant: String,
        category: SpendCategory,
        currency: CurrencyCode = CurrencyCode.INR,
        kind: TransactionKind = TransactionKind.PURCHASE,
    ) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = sourceId,
        sourceFingerprint = sourceId,
        transaction = ParsedTransaction(
            sourceId = sourceId,
            sourceReceivedAtEpochMillis = Instant.parse(instant).toEpochMilli(),
            money = Money(amountMinor = amountMinor, currency = currency),
            direction = TransactionDirection.DEBIT,
            kind = kind,
            category = category,
            merchant = merchant,
            accountHint = null,
            confidence = 0.90,
            parserVersion = 1,
        ),
    )
}
