package com.spendtracker.core.parser

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinancialMessageParserTest {
    private val parser = FinancialMessageParser()

    @Test
    fun parsesIndianRupeeCardPurchaseAndCategory() {
        val parsed = parser.parse(
            message("INR 499.00 spent on Credit Card xx1234 at NETFLIX on 04-Sep-26"),
        )

        assertNotNull(parsed)
        assertEquals(49_900, parsed.money.amountMinor)
        assertEquals(CurrencyCode.INR, parsed.money.currency)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals(TransactionKind.PURCHASE, parsed.kind)
        assertEquals(SpendCategory.SUBSCRIPTIONS, parsed.category)
        assertEquals("NETFLIX", parsed.merchant)
        assertTrue(parsed.isIncludedInSpend)
    }

    @Test
    fun parsesCommaSeparatedRupeeAmountExactly() {
        val parsed = parser.parse(
            message("Rs.1,250.50 debited from A/c XX9876. Info: SWIGGY"),
        )

        assertNotNull(parsed)
        assertEquals(125_050, parsed.money.amountMinor)
        assertEquals(SpendCategory.FOOD_AND_DINING, parsed.category)
    }

    @Test
    fun recordsCreditButDoesNotCountItAsSpend() {
        val parsed = parser.parse(
            message("INR 10,000.00 credited to your account 1234 on 04-Sep-26"),
        )

        assertNotNull(parsed)
        assertEquals(TransactionDirection.CREDIT, parsed.direction)
        assertEquals(TransactionKind.TRANSFER, parsed.kind)
        assertFalse(parsed.isIncludedInSpend)
    }

    @Test
    fun separatesCashWithdrawalFromSpend() {
        val parsed = parser.parse(
            message("INR 2,000 withdrawn from ATM using card ending 1234"),
        )

        assertNotNull(parsed)
        assertEquals(TransactionKind.CASH_WITHDRAWAL, parsed.kind)
        assertFalse(parsed.isIncludedInSpend)
    }

    @Test
    fun preservesForeignCurrencyWithoutConvertingIt() {
        val parsed = parser.parse(
            message("USD 25.00 spent on card xx1234 at GITHUB on 04-Sep-26"),
        )

        assertNotNull(parsed)
        assertEquals(2_500, parsed.money.amountMinor)
        assertEquals(CurrencyCode.USD, parsed.money.currency)
    }

    @Test
    fun ignoresOtpAndFailedTransactions() {
        assertNull(parser.parse(message("OTP 123456 for INR 2,000 purchase at STORE")))
        assertNull(parser.parse(message("Your INR 500 transaction was declined")))
    }

    private fun message(body: String) = SourceMessage(
        sourceId = "42",
        sender = "BANK",
        body = body,
        receivedAtEpochMillis = 1_788_457_600_000,
    )
}

