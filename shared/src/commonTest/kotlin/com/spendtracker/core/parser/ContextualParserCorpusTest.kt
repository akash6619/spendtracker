package com.spendtracker.core.parser

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.TransactionReviewReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Synthetic, labelled regression pilot for amount roles and merchant boundaries.
 * Reports only fixture IDs and aggregate scores. Variants exercise normalization,
 * not independent template coverage or a held-out model evaluation.
 */
class ContextualParserCorpusTest {
    private val parser = FinancialMessageParser()

    @Test
    fun labelledPaymentsKeepTransactionAmountAndMerchant() {
        val cases = listOf(
            Payment("plain", "INR 500 paid at NORTHSTAR", "NORTHSTAR"),
            Payment("balance-after", "INR 500 paid at NORTHSTAR. Available balance INR 12,000", "NORTHSTAR"),
            Payment("balance-before", "Available balance INR 12,000. INR 500 paid at NORTHSTAR", "NORTHSTAR"),
            Payment("balance-inline", "INR 500 paid at NORTHSTAR Avl Bal: INR 12,000", "NORTHSTAR"),
            Payment("abbreviated-balance", "INR 500 paid at NORTHSTAR; Avl. Bal. Rs.12,000", "NORTHSTAR"),
            Payment("current-balance", "Current balance is INR 12,000; INR 500 paid at NORTHSTAR", "NORTHSTAR"),
            Payment("closing-balance", "INR 500 paid at NORTHSTAR. Closing balance: INR 12,000", "NORTHSTAR"),
            Payment("account-balance", "INR 500 paid at NORTHSTAR. Account balance = INR 12,000", "NORTHSTAR"),
            Payment("limit-after", "INR 500 paid at NORTHSTAR. Available credit limit INR 12,000", "NORTHSTAR"),
            Payment("limit-before", "Credit limit: INR 12,000. INR 500 paid at NORTHSTAR", "NORTHSTAR"),
            Payment("limit-inline", "INR 500 paid at NORTHSTAR Available limit: INR 12,000", "NORTHSTAR"),
            Payment("two-context-values", "Credit limit INR 20,000. INR 500 paid at NORTHSTAR. Avl bal INR 12,000", "NORTHSTAR"),
            Payment("foreign-balance", "INR 500 paid at NORTHSTAR. Available balance USD 120", "NORTHSTAR"),
            Payment("unsupported-context", "INR 500 paid at NORTHSTAR. Available balance AUD 120", "NORTHSTAR"),
            Payment("malformed-context", "INR 500 paid at NORTHSTAR. Available balance INR 12,34", "NORTHSTAR"),
            Payment("bill-payment", "INR 500 paid for outstanding balance at NORTHSTAR", "NORTHSTAR"),
            Payment("balance-event", "Available balance INR 500 debited at NORTHSTAR", "NORTHSTAR"),
            Payment("card-before-merchant", "INR 500 spent on Credit Card xx1234 at NORTHSTAR on 08-Sep-26", "NORTHSTAR"),
            Payment("debit-card-before-merchant", "INR 500 spent on Debit Card xx1234 at NORTHSTAR", "NORTHSTAR"),
            Payment("instrument-only", "INR 500 spent on Credit Card xx1234", null),
            Payment("account-not-merchant", "INR 500 refunded to account ending 1234", null, included = false),
            Payment("on-merchant", "INR 500 spent on NORTHSTAR ref 456789", "NORTHSTAR"),
            Payment("info-merchant", "INR 500 debited from A/c XX1234. Info: NORTHSTAR; Avl Bal INR 12,000", "NORTHSTAR"),
            Payment("date-before-merchant", "INR 500 debited on 08-09-26 at NORTHSTAR", "NORTHSTAR"),
            Payment("reference-boundary", "INR 500 paid at NORTHSTAR reference 456789", "NORTHSTAR"),
            Payment("comma-boundary", "INR 500 paid at NORTHSTAR, ref 456789", "NORTHSTAR"),
            Payment("usd-payment", "USD 5 spent at NORTHSTAR. Available limit INR 12,000", "NORTHSTAR", 500, CurrencyCode.USD),
            Payment("jpy-payment", "JPY 500 spent at NORTHSTAR. Available balance INR 12,000", "NORTHSTAR", 500, CurrencyCode.JPY),
            Payment("transfer", "INR 500 transferred via NEFT to NORTHSTAR. Available balance INR 12,000", "NORTHSTAR", included = false),
            Payment("fee", "INR 500 fee debited from account XX1234. Available balance INR 12,000", null),
        )
        val failures = mutableListOf<String>()
        var amountMatches = 0
        var merchantMatches = 0
        var inclusionMatches = 0
        var cleanAmounts = 0
        cases.forEach { case ->
            variants(case.body).forEachIndexed { index, body ->
                val parsed = parser.parse(message(body))
                val amount = parsed?.money?.amountMinor == case.minor && parsed.money.currency == case.currency
                val merchant = parsed != null && parsed.merchant == case.merchant
                val inclusion = parsed?.includedInSpend == case.included
                val clean = parsed != null && TransactionReviewReason.CONFLICTING_AMOUNTS !in parsed.reviewReasons
                if (amount) amountMatches++
                if (merchant) merchantMatches++
                if (inclusion) inclusionMatches++
                if (clean) cleanAmounts++
                if (!(amount && merchant && inclusion && clean)) failures += "${case.id}/$index"
            }
        }
        println("PAR-01A payments=${cases.size * 3} amount=$amountMatches merchant=$merchantMatches inclusion=$inclusionMatches unambiguousAmount=$cleanAmounts complete=${cases.size * 3 - failures.size}")
        assertTrue(failures.isEmpty(), "Failed synthetic fixture IDs: $failures")
    }

    @Test
    fun negativeAndAmbiguousMessagesStayOutOfSpend() {
        val rejected = listOf(
            "OTP 123456 for INR 500 purchase at NORTHSTAR",
            "INR 500 transaction declined. Available balance INR 12,000",
            "Pending transaction INR 500 at NORTHSTAR",
            "Your available balance is INR 12,000",
            "INR 12,34 paid at NORTHSTAR. Available balance INR 12,000",
            "INR 12.345 paid at NORTHSTAR. Available balance INR 12,000",
            "AUD 500 paid at NORTHSTAR. Available balance INR 12,000",
            "INR 999999999999999999999 paid at NORTHSTAR. Avl Bal INR 12,000",
            "Payment declined at NORTHSTAR. Available credit limit INR 12,000",
            "Welcome to Northstar Bank",
            "INR 500 paid at NORTHSTAR was cancelled",
            "Available balance INR 12,000. Purchase completed at NORTHSTAR",
        )
        val ambiguous = listOf(
            "INR 500 debited and INR 400 credited at NORTHSTAR",
            "INR 500 paid at NORTHSTAR and INR 400 paid at MOONSTONE",
            "INR 500 paid at NORTHSTAR. Fee INR 20",
            "INR 500 paid at NORTHSTAR. Total INR 520",
            "INR 500 paid at NORTHSTAR. USD 5",
            "Available balance INR 12,000. INR 500 paid at NORTHSTAR and INR 400 paid at MOONSTONE",
        )
        val failures = mutableListOf<String>()
        rejected.forEachIndexed { id, text ->
            variants(text).forEachIndexed { variant, body ->
                if (parser.classify(message(body)) !is ParseOutcome.Rejected) failures += "negative-$id/$variant"
            }
        }
        ambiguous.forEachIndexed { id, text ->
            variants(text).forEachIndexed { variant, body ->
                val parsed = parser.parse(message(body))
                if (parsed == null || parsed.includedInSpend ||
                    TransactionReviewReason.CONFLICTING_AMOUNTS !in parsed.reviewReasons
                ) failures += "ambiguous-$id/$variant"
            }
        }
        println("PAR-01A safety=${(rejected.size + ambiguous.size) * 3} passed=${(rejected.size + ambiguous.size) * 3 - failures.size}")
        assertTrue(failures.isEmpty(), "Failed synthetic fixture IDs: $failures")
    }

    @Test
    fun balanceAndLimitMetadataPreserveAllParsedFactsAcrossEventKinds() {
        val events = listOf(
            "INR 500 paid at NORTHSTAR",
            "INR 500 fee debited from account XX1234",
            "INR 500 transferred via NEFT to NORTHSTAR",
            "INR 500 refunded to card ending 1234",
            "INR 500 withdrawn from ATM using card ending 1234",
            "INR 500 credited to your account 1234",
            "USD 5 spent at NORTHSTAR",
            "JPY 500 spent at NORTHSTAR",
        )
        val metadata = listOf(
            "Available balance: INR 12,000",
            "Avl. Bal. Rs. 12,000",
            "Credit limit INR 20,000",
            "Available limit: USD 120",
        )
        events.forEachIndexed { eventIndex, event ->
            val expected = assertNotNull(parser.parse(message(event)))
            metadata.forEachIndexed { labelIndex, label ->
                listOf("$label; $event", "$event; $label").forEachIndexed { position, body ->
                    assertEquals(expected, parser.parse(message(body)), "event-$eventIndex/label-$labelIndex/position-$position")
                }
            }
        }
    }

    @Test
    fun contextualValueCannotSupplyAMissingTransactionAmount() {
        assertEquals(
            ParseOutcome.Rejected(RejectionReason.MISSING_AMOUNT),
            parser.classify(message("Paid at NORTHSTAR. Available balance INR 12,000")),
        )
    }

    private fun variants(body: String) = listOf(body, body.lowercase(), "  ${body.replace(" ", "\t  ")}\n")
    private fun message(body: String) = SourceMessage("synthetic", "SYNTHETIC", body, 1_788_825_600_000)

    private data class Payment(
        val id: String,
        val body: String,
        val merchant: String?,
        val minor: Long = 50_000,
        val currency: CurrencyCode = CurrencyCode.INR,
        val included: Boolean = true,
    )
}
