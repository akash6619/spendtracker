package com.spendtracker.core.categorization

import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason

/**
 * Produces a stable, privacy-safe merchant identity for category matching.
 *
 * It uppercases text, replaces punctuation with spaces, collapses whitespace,
 * and removes common payment-handle/legal suffix noise. It receives only the
 * parser-extracted merchant, never a sender or raw message body.
 */
class MerchantNormalizer {
    fun normalize(merchant: String?): String? {
        val normalized = merchant
            ?.uppercase()
            ?.replace(NON_ALPHANUMERIC, " ")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.replace(TRAILING_NOISE, "")
            ?.trim()
        return normalized?.takeIf { it.length >= 2 }
    }

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^A-Z0-9 ]")
        val WHITESPACE = Regex("\\s+")
        val TRAILING_NOISE = Regex("(?:\\s+(?:PVT|PRIVATE|LTD|LIMITED|UPI|PAYMENT|INDIA))+$")
    }
}

/**
 * Versioned built-in category policy for parser-produced transaction facts.
 *
 * Fee semantics always win. Other rules inspect only a normalized merchant and
 * return exactly one of the fixed MVP buckets, with [SpendCategory.OTHER] as the
 * deterministic fallback. User-approved rules are applied later by the repository.
 */
class TransactionCategorizer {
    val version: Int = 1

    fun categorize(kind: TransactionKind, normalizedMerchant: String?): SpendCategory {
        if (kind == TransactionKind.FEE) return SpendCategory.FEES_AND_CHARGES
        return RULES.firstOrNull { (pattern, _) ->
            normalizedMerchant != null && pattern.containsMatchIn(normalizedMerchant)
        }?.second ?: SpendCategory.OTHER
    }

    private companion object {
        val RULES = listOf(
            Regex("\\b(?:SWIGGY|ZOMATO|RESTAURANT|CAFE|COFFEE|DINING)\\b") to SpendCategory.FOOD_AND_DINING,
            Regex("\\b(?:BIGBASKET|BLINKIT|ZEPTO|GROCERY|GROCERIES|SUPERMARKET)\\b") to SpendCategory.GROCERIES,
            Regex("\\b(?:UBER|OLA|METRO|FUEL|PETROL|DIESEL|RAPIDO)\\b") to SpendCategory.TRANSPORT,
            Regex("\\b(?:AMAZON|FLIPKART|MYNTRA|SHOPPING|RETAIL)\\b") to SpendCategory.SHOPPING,
            Regex("\\b(?:ELECTRICITY|BROADBAND|RECHARGE|UTILITY|MOBILE BILL|WATER BILL)\\b") to SpendCategory.BILLS_AND_UTILITIES,
            Regex("\\b(?:RENT|HOUSING|MAINTENANCE)\\b") to SpendCategory.HOUSING,
            Regex("\\b(?:HOSPITAL|PHARMACY|MEDICAL|MEDICINE|CLINIC)\\b") to SpendCategory.HEALTH,
            Regex("\\b(?:CINEMA|MOVIE|GAMING|BOOKMYSHOW)\\b") to SpendCategory.ENTERTAINMENT,
            Regex("\\b(?:HOTEL|FLIGHT|AIRLINE|MAKEMYTRIP|GOIBIBO|TRAVEL)\\b") to SpendCategory.TRAVEL,
            Regex("\\b(?:SCHOOL|COLLEGE|COURSE|TUITION|EDUCATION)\\b") to SpendCategory.EDUCATION,
            Regex("\\b(?:NETFLIX|SPOTIFY|HOTSTAR|SUBSCRIPTION)\\b") to SpendCategory.SUBSCRIPTIONS,
        )
    }
}

/** Applies an approved merchant rule without overriding fee semantics or unrelated review flags. */
fun ParsedTransaction.withMerchantCategory(category: SpendCategory?): ParsedTransaction {
    if (category == null || kind == TransactionKind.FEE) return this
    val remainingReasons = reviewReasons - TransactionReviewReason.UNKNOWN_CATEGORY
    val resolvedConfidence = when {
        remainingReasons.any {
            it == TransactionReviewReason.CONFLICTING_AMOUNTS ||
                it == TransactionReviewReason.CONFLICTING_DIRECTIONS
        } -> 0.35
        remainingReasons.isNotEmpty() -> 0.60
        else -> 0.90
    }
    return copy(
        category = category,
        reviewReasons = remainingReasons,
        confidence = resolvedConfidence,
    )
}
