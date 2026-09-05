package com.spendtracker.core.parser

import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind

/**
 * Converts supported financial SMS patterns into deterministic transaction data.
 *
 * It extracts exact money, direction, kind, merchant, account hint, category,
 * and confidence using local rules. Messages missing a safe amount, supported
 * currency, or direction are rejected instead of becoming unreliable records.
 */
class FinancialMessageParser {
    /** Returns null when a critical financial field cannot be mapped safely. */
    fun parse(message: SourceMessage): ParsedTransaction? {
        val normalized = message.body.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank() || FAILURE_OR_AUTH_ONLY.containsMatchIn(normalized)) return null

        val amount = extractMoney(normalized) ?: return null
        val kind = detectKind(normalized)
        val direction = detectDirection(normalized, kind) ?: return null
        val merchant = extractMerchant(normalized)
        val category = categorize(normalized, kind)

        return ParsedTransaction(
            sourceId = message.sourceId,
            sourceReceivedAtEpochMillis = message.receivedAtEpochMillis,
            money = amount,
            direction = direction,
            kind = kind,
            category = category,
            merchant = merchant,
            accountHint = ACCOUNT_HINT.find(normalized)?.groupValues?.get(1),
            confidence = confidence(kind, merchant),
            parserVersion = PARSER_VERSION,
        )
    }

    private fun extractMoney(text: String): Money? {
        val match = AMOUNT.find(text) ?: return null
        val currency = when (match.groups[1]?.value?.uppercase()) {
            "₹", "RS", "RS.", "INR" -> CurrencyCode.INR
            "$", "USD" -> CurrencyCode.USD
            "€", "EUR" -> CurrencyCode.EUR
            "£", "GBP" -> CurrencyCode.GBP
            "JPY" -> CurrencyCode.JPY
            else -> return null
        }

        val rawAmount = match.groups[2]?.value?.replace(",", "") ?: return null
        val amountMinor = decimalToMinorUnits(rawAmount, currency.fractionDigits) ?: return null
        return Money(amountMinor = amountMinor, currency = currency)
    }

    private fun decimalToMinorUnits(value: String, fractionDigits: Int): Long? {
        val parts = value.split('.', limit = 2)
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()

        if (fraction.length > fractionDigits) return null
        // Convert without floating-point arithmetic to keep stored totals exact.
        val normalizedFraction = fraction.padEnd(fractionDigits, '0')
        val scale = powerOfTen(fractionDigits)
        val fractionValue = normalizedFraction.toLongOrNull() ?: 0L
        return whole.timesOrNull(scale)?.plusOrNull(fractionValue)
    }

    private fun detectDirection(text: String, kind: TransactionKind): TransactionDirection? = when {
        kind == TransactionKind.REFUND -> TransactionDirection.CREDIT
        DEBIT_WORDS.containsMatchIn(text) -> TransactionDirection.DEBIT
        CREDIT_WORDS.containsMatchIn(text) -> TransactionDirection.CREDIT
        else -> null
    }

    private fun detectKind(text: String): TransactionKind = when {
        REFUND_WORDS.containsMatchIn(text) -> TransactionKind.REFUND
        CASH_WORDS.containsMatchIn(text) -> TransactionKind.CASH_WITHDRAWAL
        FEE_WORDS.containsMatchIn(text) -> TransactionKind.FEE
        TRANSFER_WORDS.containsMatchIn(text) -> TransactionKind.TRANSFER
        PURCHASE_WORDS.containsMatchIn(text) -> TransactionKind.PURCHASE
        else -> TransactionKind.UNKNOWN
    }

    private fun categorize(text: String, kind: TransactionKind): SpendCategory {
        // Fees take precedence; unmatched merchants deliberately fall back to Other.
        if (kind == TransactionKind.FEE) return SpendCategory.FEES_AND_CHARGES

        return CATEGORY_RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }
            ?.second
            ?: SpendCategory.OTHER
    }

    private fun extractMerchant(text: String): String? {
        val merchant = MERCHANT.find(text)?.groups?.get(1)?.value ?: return null
        return merchant.trim(' ', '.', ',', '-')
            .takeIf { it.length >= 2 }
            ?.take(MAX_MERCHANT_LENGTH)
    }

    private fun confidence(kind: TransactionKind, merchant: String?): Double = when {
        kind == TransactionKind.UNKNOWN -> 0.60
        merchant == null -> 0.78
        else -> 0.90
    }

    private fun Long.timesOrNull(other: Long): Long? =
        if (this == 0L || other <= Long.MAX_VALUE / this) this * other else null

    private fun Long.plusOrNull(other: Long): Long? =
        if (other <= Long.MAX_VALUE - this) this + other else null

    private fun powerOfTen(exponent: Int): Long {
        var result = 1L
        repeat(exponent) { result *= 10L }
        return result
    }

    private companion object {
        const val PARSER_VERSION = 1
        const val MAX_MERCHANT_LENGTH = 80

        val AMOUNT = Regex(
            "(?i)(?<![A-Z0-9])(INR|Rs\\.?|₹|USD|\\$|EUR|€|GBP|£|JPY)\\s*([0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]+)?)",
        )
        val ACCOUNT_HINT = Regex(
            "(?i)(?:a/?c|account|card)(?:\\s+(?:no\\.?|number|ending|xx|x))*[\\s:*#-]*[xX*.-]*([0-9]{3,6})",
        )
        val MERCHANT = Regex(
            "(?i)(?:\\bat\\b|\\bto\\b|info:)\\s+([A-Z0-9][A-Z0-9 &@._/-]*?)(?=\\s+(?:on|using|via|ref|avl|available|from|for)\\b|[.;]|$)",
        )

        val FAILURE_OR_AUTH_ONLY = Regex(
            "(?i)\\b(?:otp|one[ -]time password|declined|failed|unsuccessful|not processed|was cancelled)\\b",
        )
        val REFUND_WORDS = Regex("(?i)\\b(?:refund(?:ed)?|reversal|reversed)\\b")
        val CASH_WORDS = Regex("(?i)\\b(?:atm|cash withdrawal|withdrawn)\\b")
        val FEE_WORDS = Regex("(?i)\\b(?:fee|fees|charge|charges|penalty)\\b")
        val TRANSFER_WORDS = Regex(
            "(?i)\\b(?:transferred|sent to|credited to|beneficiary|imps|neft|rtgs)\\b",
        )
        val PURCHASE_WORDS = Regex(
            "(?i)\\b(?:spent|paid|purchase|purchased|debited|txn|transaction)\\b",
        )
        val DEBIT_WORDS = Regex(
            "(?i)\\b(?:debited|spent|paid|purchase|purchased|withdrawn|sent|charged)\\b",
        )
        val CREDIT_WORDS = Regex(
            "(?i)\\b(?:credited|received|deposited)\\b",
        )

        val CATEGORY_RULES = listOf(
            Regex("(?i)\\b(?:swiggy|zomato|restaurant|cafe|coffee|dining)\\b") to SpendCategory.FOOD_AND_DINING,
            Regex("(?i)\\b(?:bigbasket|blinkit|zepto|grocery|groceries|supermarket)\\b") to SpendCategory.GROCERIES,
            Regex("(?i)\\b(?:uber|ola|metro|fuel|petrol|diesel|rapido)\\b") to SpendCategory.TRANSPORT,
            Regex("(?i)\\b(?:amazon|flipkart|myntra|shopping|retail)\\b") to SpendCategory.SHOPPING,
            Regex("(?i)\\b(?:electricity|broadband|recharge|utility|mobile bill|water bill)\\b") to SpendCategory.BILLS_AND_UTILITIES,
            Regex("(?i)\\b(?:rent|housing|maintenance)\\b") to SpendCategory.HOUSING,
            Regex("(?i)\\b(?:hospital|pharmacy|medical|medicine|clinic)\\b") to SpendCategory.HEALTH,
            Regex("(?i)\\b(?:cinema|movie|gaming|bookmyshow)\\b") to SpendCategory.ENTERTAINMENT,
            Regex("(?i)\\b(?:hotel|flight|airline|makemytrip|goibibo|travel)\\b") to SpendCategory.TRAVEL,
            Regex("(?i)\\b(?:school|college|course|tuition|education)\\b") to SpendCategory.EDUCATION,
            Regex("(?i)\\b(?:netflix|spotify|hotstar|subscription)\\b") to SpendCategory.SUBSCRIPTIONS,
        )
    }
}
