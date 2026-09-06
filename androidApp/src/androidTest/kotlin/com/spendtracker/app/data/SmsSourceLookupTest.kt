package com.spendtracker.app.data

import android.database.MatrixCursor
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendtracker.core.importing.SourceFingerprinter
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/**
 * Verifies that on-demand source viewing never shows an unrelated message:
 * provider row IDs can be reused by Android, so the fetched row must reproduce
 * the stored installation-local fingerprint before its body is shown.
 */
@RunWith(AndroidJUnit4::class)
class SmsSourceLookupTest {

    @Test
    fun matchingFingerprintReturnsFoundMessage() = runBlocking {
        val lookup = SmsSourceLookup(
            queryRow = { cursor("SYNTHETIC SENDER", "SYNTHETIC MESSAGE BODY", 1_000L) },
            fingerprinter = StubFingerprinter("expected-fingerprint"),
        )
        val result = lookup.lookup(transaction(fingerprint = "expected-fingerprint"))
        assertTrue(result is SourceLookupResult.Found)
        val found = result as SourceLookupResult.Found
        assertEquals("SYNTHETIC MESSAGE BODY", found.body)
    }

    @Test
    fun reusedProviderIdWithDifferentFingerprintIsNotShown() = runBlocking {
        val lookup = SmsSourceLookup(
            queryRow = { cursor("SYNTHETIC SENDER", "UNRELATED MESSAGE BODY", 2_000L) },
            fingerprinter = StubFingerprinter("unrelated-fingerprint"),
        )
        val result = lookup.lookup(transaction(fingerprint = "expected-fingerprint"))
        assertEquals(
            SourceLookupResult.Unavailable(SourceUnavailableReason.MESSAGE_NOT_FOUND),
            result,
        )
    }

    @Test
    fun missingRowReportsMessageNotFound() = runBlocking {
        val lookup = SmsSourceLookup(
            queryRow = { null },
            fingerprinter = StubFingerprinter("expected-fingerprint"),
        )
        val result = lookup.lookup(transaction(fingerprint = "expected-fingerprint"))
        assertEquals(
            SourceLookupResult.Unavailable(SourceUnavailableReason.MESSAGE_NOT_FOUND),
            result,
        )
    }

    private fun cursor(sender: String, body: String, receivedAt: Long) = MatrixCursor(
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
    ).apply {
        addRow(arrayOf<Any>(sender, body, receivedAt))
    }

    private fun transaction(fingerprint: String) = LedgerTransaction(
        id = "ANDROID_SMS:$fingerprint",
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = "provider-1",
        sourceFingerprint = fingerprint,
        sourceReceivedAtEpochMillis = 1_000L,
        money = Money(100, CurrencyCode.INR),
        direction = TransactionDirection.DEBIT,
        kind = TransactionKind.PURCHASE,
        category = SpendCategory.FOOD_AND_DINING,
        merchant = "Synthetic Merchant",
        accountHint = null,
        confidence = 0.90,
        parserVersion = 3,
        reviewReasons = emptySet(),
        includedInSpend = true,
    )

    private class StubFingerprinter(private val result: String) : SourceFingerprinter {
        override fun fingerprint(message: com.spendtracker.core.model.SourceMessage): String = result
    }
}
