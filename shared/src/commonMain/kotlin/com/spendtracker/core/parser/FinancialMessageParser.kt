package com.spendtracker.core.parser

import com.spendtracker.core.categorization.MerchantNormalizer
import com.spendtracker.core.categorization.TransactionCategorizer
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason

/**
 * Converts ephemeral financial-message text into deterministic, explainable outcomes.
 *
 * The parser runs entirely in common Kotlin and separates normalization, message
 * detection, exact amount conversion, semantic classification, field extraction,
 * and outcome selection. It never retains or logs source text. Completed and clear
 * events become [ParseOutcome.Accepted], deterministic but ambiguous events become
 * [ParseOutcome.NeedsReview], and unsupported or incomplete messages become
 * [ParseOutcome.Rejected] with a privacy-safe reason.
 *
 * Rules are conservative and versioned. When detected behavior changes, [version]
 * advances; re-import refreshes untouched rows and preserves user-edited rows.
 * Explicit balance/limit amounts are contextual metadata, while unlabelled
 * competing amounts stay ambiguous. No source text leaves the parsing call.
 */
class FinancialMessageParser(
    private val merchantNormalizer: MerchantNormalizer = MerchantNormalizer(),
    private val categorizer: TransactionCategorizer = TransactionCategorizer(),
) {
    val version: Int = PARSER_BASE_VERSION + categorizer.version

    /** Returns the full explainable outcome used by import and diagnostics. */
    fun classify(message: SourceMessage): ParseOutcome {
        val text = normalize(message.body)
        detectEarlyRejection(text)?.let { return ParseOutcome.Rejected(it) }

        val amountResult = extractMoney(text)
        if (amountResult is AmountResult.Failure) return ParseOutcome.Rejected(amountResult.reason)
        amountResult as AmountResult.Success

        val kind = detectKind(text)
        val directionResult = detectDirection(text, kind)
            ?: return ParseOutcome.Rejected(RejectionReason.MISSING_DIRECTION)
        val merchant = merchantNormalizer.normalize(extractMerchant(text))
        val category = categorizer.categorize(kind, merchant)
        val reviewReasons = buildSet {
            if (amountResult.conflicting) add(TransactionReviewReason.CONFLICTING_AMOUNTS)
            if (directionResult.conflicting) add(TransactionReviewReason.CONFLICTING_DIRECTIONS)
            if (kind == TransactionKind.UNKNOWN) add(TransactionReviewReason.UNKNOWN_KIND)
            if (category == SpendCategory.OTHER) add(TransactionReviewReason.UNKNOWN_CATEGORY)
        }
        val parsed = ParsedTransaction(
            sourceId = message.sourceId,
            sourceReceivedAtEpochMillis = message.receivedAtEpochMillis,
            money = amountResult.money,
            direction = directionResult.direction,
            kind = kind,
            category = category,
            merchant = merchant,
            accountHint = ACCOUNT_HINT.find(text)?.groupValues?.get(1),
            confidence = confidence(reviewReasons),
            parserVersion = version,
            // Conflicting monetary facts stay visible but cannot affect totals before confirmation.
            includedInSpend = reviewReasons.none {
                it == TransactionReviewReason.CONFLICTING_AMOUNTS ||
                    it == TransactionReviewReason.CONFLICTING_DIRECTIONS
            } && directionResult.direction == TransactionDirection.DEBIT &&
                kind in setOf(TransactionKind.PURCHASE, TransactionKind.FEE),
            reviewReasons = reviewReasons,
        )
        return if (reviewReasons.isEmpty()) ParseOutcome.Accepted(parsed)
        else ParseOutcome.NeedsReview(parsed, reviewReasons)
    }

    /** Compatibility helper; rejected input maps to null and reviewable input remains visible. */
    fun parse(message: SourceMessage): ParsedTransaction? = when (val outcome = classify(message)) {
        is ParseOutcome.Accepted -> outcome.transaction
        is ParseOutcome.NeedsReview -> outcome.transaction
        is ParseOutcome.Rejected -> null
    }

    private fun normalize(body: String): String = body.replace(Regex("\\s+"), " ").trim()

    private fun detectEarlyRejection(text: String): RejectionReason? = when {
        text.isBlank() -> RejectionReason.EMPTY
        OTP_OR_AUTHORIZATION.containsMatchIn(text) -> RejectionReason.OTP_OR_AUTHORIZATION
        FAILED_OR_CANCELLED.containsMatchIn(text) -> RejectionReason.FAILED_OR_CANCELLED
        BALANCE_ONLY.containsMatchIn(text) && !COMPLETED_EVENT.containsMatchIn(text) -> RejectionReason.BALANCE_ONLY
        !FINANCIAL_SIGNAL.containsMatchIn(text) -> RejectionReason.NON_FINANCIAL
        else -> null
    }

    private fun extractMoney(text: String): AmountResult {
        val tokens = AMOUNT_TOKEN.findAll(text).toList()
        if (tokens.isEmpty()) {
            return if (CURRENCY_SIGNAL.containsMatchIn(text)) AmountResult.Failure(RejectionReason.MALFORMED_AMOUNT)
            else AmountResult.Failure(RejectionReason.MISSING_AMOUNT)
        }
        val matches = tokens.filterNot { isContextAmount(text, it) }
        if (matches.isEmpty()) return AmountResult.Failure(RejectionReason.MISSING_AMOUNT)
        val parsed = matches.map { match ->
            val currency = currency(match.groupValues[1])
                ?: return AmountResult.Failure(RejectionReason.UNSUPPORTED_CURRENCY)
            val normalized = normalizeNumber(match.groupValues[2])
                ?: return AmountResult.Failure(RejectionReason.MALFORMED_AMOUNT)
            if (normalized.substringAfter('.', "").length > currency.fractionDigits) {
                return AmountResult.Failure(RejectionReason.MALFORMED_AMOUNT)
            }
            val minor = decimalToMinorUnits(normalized, currency.fractionDigits)
                ?: return AmountResult.Failure(RejectionReason.AMOUNT_OVERFLOW)
            Money(minor, currency)
        }
        return AmountResult.Success(parsed.first(), parsed.distinct().size > 1)
    }

    private fun isContextAmount(text: String, amount: MatchResult): Boolean {
        // Only an adjacent, explicit label can remove an amount from consideration.
        // In particular, "paid for outstanding balance" is still a payment.
        val before = text.substring((amount.range.first - 100).coerceAtLeast(0), amount.range.first)
        if (!CONTEXT_AMOUNT_LABEL.containsMatchIn(before)) return false
        val afterStart = amount.range.last + 1
        val after = text.substring(afterStart, (afterStart + 60).coerceAtMost(text.length))
        // A completed verb directly attached to the value outweighs its prefix:
        // "Available balance INR 500 debited" describes money actually moved.
        return !AMOUNT_EVENT_SUFFIX.containsMatchIn(after)
    }

    private fun currency(token: String): CurrencyCode? = when (token.uppercase()) {
        "₹", "RS", "RS.", "INR" -> CurrencyCode.INR
        "$", "USD" -> CurrencyCode.USD
        "€", "EUR" -> CurrencyCode.EUR
        "£", "GBP" -> CurrencyCode.GBP
        "JPY", "¥" -> CurrencyCode.JPY
        else -> null
    }

    private fun normalizeNumber(value: String): String? {
        if (!NUMBER_FORMAT.matches(value)) return null
        return value.replace(",", "")
    }

    private fun decimalToMinorUnits(value: String, fractionDigits: Int): Long? {
        val parts = value.split('.', limit = 2)
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.length > fractionDigits) return null
        val normalizedFraction = fraction.padEnd(fractionDigits, '0')
        val scale = powerOfTen(fractionDigits)
        val fractionValue = normalizedFraction.toLongOrNull() ?: 0L
        return whole.timesOrNull(scale)?.plusOrNull(fractionValue)
    }

    private fun detectDirection(text: String, kind: TransactionKind): DirectionResult? {
        val hasDebit = DEBIT_WORDS.containsMatchIn(text)
        val hasCredit = CREDIT_WORDS.containsMatchIn(text)
        val direction = when {
            kind == TransactionKind.REFUND -> TransactionDirection.CREDIT
            hasDebit -> TransactionDirection.DEBIT
            hasCredit -> TransactionDirection.CREDIT
            else -> return null
        }
        return DirectionResult(direction, hasDebit && hasCredit && kind != TransactionKind.REFUND)
    }

    private fun detectKind(text: String): TransactionKind = when {
        REFUND_WORDS.containsMatchIn(text) -> TransactionKind.REFUND
        CASH_WORDS.containsMatchIn(text) -> TransactionKind.CASH_WITHDRAWAL
        FEE_WORDS.containsMatchIn(text) -> TransactionKind.FEE
        TRANSFER_WORDS.containsMatchIn(text) -> TransactionKind.TRANSFER
        PURCHASE_WORDS.containsMatchIn(text) -> TransactionKind.PURCHASE
        else -> TransactionKind.UNKNOWN
    }

    private fun extractMerchant(text: String): String? {
        return MERCHANT.findAll(text)
            .mapNotNull { match ->
                match.groups[1]?.value
                    ?.trim(' ', '.', ',', '-')
                    ?.takeIf { it.length >= 2 }
                    ?.take(MAX_MERCHANT_LENGTH)
            }
            // An earlier "on <card>" phrase describes the payment instrument,
            // so keep looking for a later merchant anchor such as "at <store>".
            .firstOrNull { !PAYMENT_INSTRUMENT_MERCHANT.containsMatchIn(it) }
    }

    private fun confidence(reasons: Set<TransactionReviewReason>): Double = when {
        reasons.any {
            it == TransactionReviewReason.CONFLICTING_AMOUNTS ||
                it == TransactionReviewReason.CONFLICTING_DIRECTIONS
        } -> 0.35
        reasons.isNotEmpty() -> 0.60
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

    /** Internal exact-money stage result; source text never appears in either variant. */
    private sealed interface AmountResult {
        /** One deterministic money value plus whether other distinct values were present. */
        data class Success(val money: Money, val conflicting: Boolean) : AmountResult

        /** A safe explanation for why no exact supported money value could be produced. */
        data class Failure(val reason: RejectionReason) : AmountResult
    }

    /** Internal direction choice plus a flag that forces conflicting wording into review. */
    private data class DirectionResult(val direction: TransactionDirection, val conflicting: Boolean)

    private companion object {
        const val PARSER_BASE_VERSION = 4
        const val MAX_MERCHANT_LENGTH = 80

        val AMOUNT_TOKEN = Regex("(?i)(?<![A-Z0-9])(INR|Rs\\.?|₹|USD|\\$|EUR|€|GBP|£|JPY|¥|AUD|CAD)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")
        val NUMBER_FORMAT = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{2})*,[0-9]{3}|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")
        val CURRENCY_SIGNAL = Regex("(?i)(?:₹|\\$|€|£|¥|\\b(?:INR|RS\\.?|USD|EUR|GBP|JPY|AUD|CAD)\\b)")
        val CONTEXT_AMOUNT_LABEL = Regex(
            """(?i)\b(?:(?:available|avail|avl|current|closing|remaining|ledger|clear|account)\.?\s+(?:balance|bal)\.?|(?:(?:available|avl|total)\.?\s+)?credit\s+limit|available\s+limit)\s*(?:(?:is|of)\s*)?[:=]?\s*$""",
        )
        val AMOUNT_EVENT_SUFFIX = Regex(
            """(?i)^\s*(?:(?:was|is|has\s+been)\s+)?(?:debited|credited|spent|paid|withdrawn|charged|received|refunded|reversed|transferred)\b""",
        )
        val ACCOUNT_HINT = Regex("(?i)(?:a/?c|account|card)(?:\\s+(?:no\\.?|number|ending|xx|x))*[\\s:*#-]*[xX*.-]*([0-9]{3,6})")
        val MERCHANT = Regex(
            """(?i)(?:\bat\b|\bto\b|\bon\b|info:)\s+(?!\d|your\b|my\b|the\b|this\b|(?:credit\s+|debit\s+)?card\b|account\b|a/?c\b)([A-Z0-9][A-Z0-9 &@._/-]*?)(?=\s+(?:at|on|using|via|ref|reference|avl|avail|available|current|closing|account|credit\s+limit|from|for|was)\b|[.,;]|$)""",
        )
        val PAYMENT_INSTRUMENT_MERCHANT = Regex(
            """(?i)\b(?:(?:credit|debit|bank)\s+)?card\b|\b(?:account|a/?c)\b""",
        )
        val OTP_OR_AUTHORIZATION = Regex("(?i)\\b(?:otp|one[ -]time password|verification code|do not share|authorization only|pre-?authori[sz](?:ation|ed)|pending transaction)\\b")
        val FAILED_OR_CANCELLED = Regex("(?i)\\b(?:declined|failed|unsuccessful|not processed|cancelled|canceled|rejected)\\b")
        val BALANCE_ONLY = Regex("(?i)\\b(?:available|avl|current|closing)\\s+(?:balance|bal)\\b|\\bbalance enquiry\\b")
        val COMPLETED_EVENT = Regex("(?i)\\b(?:debited|credited|spent|paid|withdrawn|charged|received|refunded|reversed|transferred)\\b")
        val FINANCIAL_SIGNAL = Regex("(?i)\\b(?:debited|credited|spent|paid|purchase|withdrawn|charged|received|refunded|reversal|reversed|transferred|sent|fee|penalty|imps|neft|rtgs|upi|txn|transaction)\\b")
        val REFUND_WORDS = Regex("(?i)\\b(?:refund(?:ed)?|reversal|reversed)\\b")
        val CASH_WORDS = Regex("(?i)\\b(?:atm|cash withdrawal|withdrawn)\\b")
        val FEE_WORDS = Regex("(?i)\\b(?:fee|fees|charge|charges|penalty)\\b")
        val TRANSFER_WORDS = Regex("(?i)\\b(?:transferred|sent to|credited to|beneficiary|imps|neft|rtgs|upi transfer)\\b")
        val PURCHASE_WORDS = Regex("(?i)\\b(?:spent|paid|purchase|purchased|debited|charged|txn|transaction|upi payment)\\b")
        val DEBIT_WORDS = Regex("(?i)\\b(?:debited|spent|paid|purchase|purchased|withdrawn|sent|transferred|charged)\\b")
        val CREDIT_WORDS = Regex("(?i)\\b(?:credited|received|deposited|refunded|reversed)\\b")
    }
}
