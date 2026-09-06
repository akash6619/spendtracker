package com.spendtracker.core.parser

import com.spendtracker.core.categorization.TransactionCategorizer
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Defines the portable parser contract with synthetic, table-driven examples.
 * Each positive template has a nearby negative or incomplete variant, while
 * mutation-style cases ensure normalization and malformed input never crash.
 */
class FinancialMessageParserTest {
    private val parser = FinancialMessageParser()

    @Test
    fun parserVersionIncludesCategorizationPolicyVersion() {
        assertEquals(2 + TransactionCategorizer().version, parser.version)
    }

    @Test
    fun acceptsRepresentativeIndiaFirstCompletedEvents() {
        val cases = listOf(
            Case("INR 499.00 spent on Credit Card xx1234 at NETFLIX on 04-Sep-26", TransactionKind.PURCHASE, true),
            Case("Rs.1,250.50 debited from A/c XX9876. Info: SWIGGY", TransactionKind.PURCHASE, true),
            Case("₹725 paid via UPI payment to NORTHSTAR CAFE", TransactionKind.PURCHASE, true),
            Case("INR 199.00 fee debited from account XX4567", TransactionKind.FEE, true),
            Case("INR 850 refunded to card ending 7654", TransactionKind.REFUND, false),
            Case("INR 10,000.00 credited to your account 1234", TransactionKind.TRANSFER, false),
            Case("INR 2,000 withdrawn from ATM using card ending 1234", TransactionKind.CASH_WITHDRAWAL, false),
            Case("INR 3,500 transferred via NEFT to beneficiary NORTHSTAR", TransactionKind.TRANSFER, false),
        )

        cases.forEach { case ->
            val outcome = parser.classify(message(case.body))
            val parsed = outcome.transactionOrNull()
            assertTrue(parsed != null, case.body)
            assertEquals(case.kind, parsed.kind, case.body)
            assertEquals(case.included, parsed.isIncludedInSpend, case.body)
            assertEquals(3, parsed.parserVersion)
        }
    }

    @Test
    fun rejectsNearbyNonCompletedAndNonFinancialMessagesWithReasons() {
        val cases = listOf(
            "OTP 123456 for INR 2,000 purchase at STORE" to RejectionReason.OTP_OR_AUTHORIZATION,
            "INR 500 transaction was declined" to RejectionReason.FAILED_OR_CANCELLED,
            "Your available balance is INR 5,000" to RejectionReason.BALANCE_ONLY,
            "Pre-authorisation for USD 25.00 at HOTEL" to RejectionReason.OTP_OR_AUTHORIZATION,
            "Get cashback offers on your next purchase" to RejectionReason.MISSING_AMOUNT,
            "Welcome to Northstar Bank" to RejectionReason.NON_FINANCIAL,
            "INR 10.00 transaction" to RejectionReason.MISSING_DIRECTION,
        )
        cases.forEach { (body, reason) ->
            assertEquals(ParseOutcome.Rejected(reason), parser.classify(message(body)), body)
            assertNull(parser.parse(message(body)), body)
        }
    }

    @Test
    fun parsesExactMinorUnitsAcrossAliasesGroupingAndCurrencies() {
        val cases = listOf(
            Triple("₹1,23,456.78 paid at NORTHSTAR", CurrencyCode.INR, 12_345_678L),
            Triple("INR 123,456.78 paid at NORTHSTAR", CurrencyCode.INR, 12_345_678L),
            Triple("Rs 12.5 paid at NORTHSTAR", CurrencyCode.INR, 1_250L),
            Triple("USD 25.00 spent at NORTHSTAR", CurrencyCode.USD, 2_500L),
            Triple("EUR 9.99 spent at NORTHSTAR", CurrencyCode.EUR, 999L),
            Triple("GBP 7 spent at NORTHSTAR", CurrencyCode.GBP, 700L),
            Triple("JPY 2500 spent at NORTHSTAR", CurrencyCode.JPY, 2_500L),
            Triple("¥2500 spent at NORTHSTAR", CurrencyCode.JPY, 2_500L),
        )
        cases.forEach { (body, currency, minor) ->
            val parsed = parser.classify(message(body)).transactionOrNull()
            assertEquals(currency, parsed?.money?.currency, body)
            assertEquals(minor, parsed?.money?.amountMinor, body)
        }
    }

    @Test
    fun rejectsMalformedUnsupportedAndOverflowingAmounts() {
        val cases = listOf(
            "INR 12,34 paid at NORTHSTAR" to RejectionReason.MALFORMED_AMOUNT,
            "INR 12.345 paid at NORTHSTAR" to RejectionReason.MALFORMED_AMOUNT,
            "JPY 10.5 paid at NORTHSTAR" to RejectionReason.MALFORMED_AMOUNT,
            "AUD 20.00 paid at NORTHSTAR" to RejectionReason.UNSUPPORTED_CURRENCY,
            "INR 999999999999999999999 paid at NORTHSTAR" to RejectionReason.AMOUNT_OVERFLOW,
        )
        cases.forEach { (body, reason) ->
            assertEquals(ParseOutcome.Rejected(reason), parser.classify(message(body)), body)
        }
    }

    @Test
    fun ambiguousFactsAreReviewableAndNotSilentlyOrdinary() {
        val amountConflict = assertIs<ParseOutcome.NeedsReview>(
            parser.classify(message("INR 500 debited and INR 400 credited at NORTHSTAR")),
        )
        assertTrue(TransactionReviewReason.CONFLICTING_AMOUNTS in amountConflict.reasons)
        assertTrue(TransactionReviewReason.CONFLICTING_DIRECTIONS in amountConflict.reasons)
        assertFalse(amountConflict.transaction.isIncludedInSpend)

        val missingMerchant = assertIs<ParseOutcome.NeedsReview>(
            parser.classify(message("INR 500 purchase completed")),
        )
        assertEquals(
            setOf(
                TransactionReviewReason.MISSING_MERCHANT,
                TransactionReviewReason.UNKNOWN_CATEGORY,
            ),
            missingMerchant.reasons,
        )
    }

    @Test
    fun amountStopsBeforeWhitespaceSeparatedDateOrReferenceDigits() {
        val parsed = parser.classify(
            message("INR 500 05-09-26 debited at NORTHSTAR"),
        ).transactionOrNull()

        assertEquals(50_000, parsed?.money?.amountMinor)
    }

    @Test
    fun extractsSafeFieldsAndAppliesFeeCategoryPrecedence() {
        val outcome = parser.classify(
            message("Rs.50 fee debited from card ending XX9876 at SWIGGY"),
        )
        val parsed = assertIs<ParseOutcome.Accepted>(outcome).transaction
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals("9876", parsed.accountHint)
        assertEquals("SWIGGY", parsed.merchant)
        assertEquals(SpendCategory.FEES_AND_CHARGES, parsed.category)
    }

    @Test
    fun casingWhitespacePunctuationAndRandomMalformedTextNeverCrash() {
        val variants = listOf(
            "  inr  12.00   DEBITED at Northstar Cafe  ",
            "\nINR\t12.00 paid at NORTHSTAR;",
            "INR 12.00 PAID AT northstar.",
        )
        variants.forEach { assertTrue(parser.classify(message(it)).transactionOrNull() != null) }

        val random = Random(6619)
        val alphabet = " INR₹$.,-+_ abcXYZ0123456789\t\n"
        repeat(500) {
            val body = buildString {
                repeat(random.nextInt(0, 120)) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            parser.classify(message(body))
        }
    }

    private fun ParseOutcome.transactionOrNull() = when (this) {
        is ParseOutcome.Accepted -> transaction
        is ParseOutcome.NeedsReview -> transaction
        is ParseOutcome.Rejected -> null
    }

    private fun message(body: String) = SourceMessage("42", "SYNTHETIC", body, 1_788_457_600_000)
    private data class Case(val body: String, val kind: TransactionKind, val included: Boolean)
}
