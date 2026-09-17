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
    val version: Int = 2

    fun categorize(kind: TransactionKind, normalizedMerchant: String?): SpendCategory {
        if (kind == TransactionKind.FEE) return SpendCategory.FEES_AND_CHARGES
        return RULES.firstOrNull { (pattern, _) ->
            normalizedMerchant != null && pattern.containsMatchIn(normalizedMerchant)
        }?.second ?: SpendCategory.OTHER
    }

    private companion object {
        val RULES = listOf(
            // Concatenated provider descriptors are common in bank SMS values;
            // Instamart must precede the general Swiggy food identity.
            Regex("INSTAMART") to SpendCategory.GROCERIES,
            Regex("SWIGGY|ZOMATO|RESTAURANT|CAFE|COFFEE|DINING") to SpendCategory.FOOD_AND_DINING,
            Regex("BIGBASKET|BLINKIT|ZEPTO|GROCERY|GROCERIES|SUPERMARKET") to SpendCategory.GROCERIES,
            Regex("UBER|OLA|METRO|FUEL|PETROL|DIESEL|RAPIDO") to SpendCategory.TRANSPORT,
            Regex("AMAZON|FLIPKART|MYNTRA|SHOPPING|RETAIL") to SpendCategory.SHOPPING,
            Regex("ELECTRICITY|BROADBAND|RECHARGE|UTILITY|MOBILE BILL|WATER BILL") to SpendCategory.BILLS_AND_UTILITIES,
            Regex("RENT|HOUSING|MAINTENANCE") to SpendCategory.HOUSING,
            Regex("HOSPITAL|PHARMACY|MEDICAL|MEDICINE|CLINIC") to SpendCategory.HEALTH,
            Regex("CINEMA|MOVIE|GAMING|BOOKMYSHOW") to SpendCategory.ENTERTAINMENT,
            Regex("HOTEL|FLIGHT|AIRLINE|MAKEMYTRIP|GOIBIBO|TRAVEL") to SpendCategory.TRAVEL,
            Regex("SCHOOL|COLLEGE|COURSE|TUITION|EDUCATION") to SpendCategory.EDUCATION,
            Regex("NETFLIX|SPOTIFY|HOTSTAR|SUBSCRIPTION") to SpendCategory.SUBSCRIPTIONS,
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
