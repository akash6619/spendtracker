package com.spendtracker.app.ui

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
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
        transaction = ParsedTransaction(
        sourceId = "preview-transaction",
        sourceReceivedAtEpochMillis = Instant.parse("2026-09-04T07:45:00Z").toEpochMilli(),
        money = Money(amountMinor = 685_00, currency = CurrencyCode.INR),
        direction = TransactionDirection.DEBIT,
        kind = TransactionKind.PURCHASE,
        category = SpendCategory.FOOD_AND_DINING,
        merchant = "Northstar Cafe",
        accountHint = null,
        confidence = 0.90,
        parserVersion = 1,
        ),
    )
}
