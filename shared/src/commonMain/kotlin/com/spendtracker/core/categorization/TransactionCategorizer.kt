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
    val version: Int = 3

    fun categorize(kind: TransactionKind, normalizedMerchant: String?): SpendCategory {
        return resolve(kind, normalizedMerchant).category
    }

    /** Returns category plus stable identity only when a recognized merchant brand matched. */
    fun resolve(kind: TransactionKind, normalizedMerchant: String?): MerchantCategorization {
        if (kind == TransactionKind.FEE) {
            return MerchantCategorization(normalizedMerchant, SpendCategory.FEES_AND_CHARGES)
        }
        val merchantRule = RULES.firstOrNull {
            it.canonicalMerchant != null && normalizedMerchant?.contains(it.keyword) == true
        }
        val categoryRule = merchantRule ?: RULES.firstOrNull {
            normalizedMerchant?.contains(it.keyword) == true
        }
        return MerchantCategorization(
            merchant = merchantRule?.canonicalMerchant ?: normalizedMerchant,
            category = categoryRule?.category ?: SpendCategory.OTHER,
        )
    }

    /** Converts a normalized extracted value or stored rule key to its stable brand identity. */
    fun canonicalizeMerchant(normalizedMerchant: String?): String? {
        if (normalizedMerchant == null) return null
        return RULES.firstOrNull {
            it.canonicalMerchant != null && normalizedMerchant.contains(it.keyword)
        }?.canonicalMerchant ?: normalizedMerchant
    }

    private companion object {
        val RULES = listOf(
            // Concatenated provider descriptors are common in bank SMS values;
            // Instamart must precede the general Swiggy food identity.
            merchantRules(SpendCategory.GROCERIES, "INSTAMART"),
            merchantRules(SpendCategory.FOOD_AND_DINING, "SWIGGY", "ZOMATO"),
            categoryRules(SpendCategory.FOOD_AND_DINING, "RESTAURANT", "CAFE", "COFFEE", "DINING"),
            merchantRules(SpendCategory.GROCERIES, "BIGBASKET", "BLINKIT", "ZEPTO"),
            merchantRules(SpendCategory.GROCERIES, "JIOMART", "DMART", "NATURESBASKET"),
            categoryRules(SpendCategory.GROCERIES, "GROCERY", "GROCERIES", "SUPERMARKET"),
            merchantRules(SpendCategory.FOOD_AND_DINING, "DOMINOS", "MCDONALDS", "KFC", "STARBUCKS", "EATSURE"),
            merchantRules(SpendCategory.TRANSPORT, "UBER", "OLA", "RAPIDO"),
            merchantRules(SpendCategory.TRANSPORT, "NAMMAYATRI", "BLUSMART", "REDBUS"),
            categoryRules(SpendCategory.TRANSPORT, "METRO", "FUEL", "PETROL", "DIESEL"),
            merchantRules(SpendCategory.SHOPPING, "AMAZON", "FLIPKART", "MYNTRA"),
            merchantRules(SpendCategory.SHOPPING, "AJIO", "MEESHO", "NYKAA", "CROMA"),
            categoryRules(SpendCategory.SHOPPING, "SHOPPING", "RETAIL"),
            categoryRules(SpendCategory.BILLS_AND_UTILITIES, "ELECTRICITY", "BROADBAND", "RECHARGE", "UTILITY", "MOBILE BILL", "WATER BILL"),
            categoryRules(SpendCategory.HOUSING, "RENT", "HOUSING", "MAINTENANCE"),
            merchantRules(SpendCategory.HEALTH, "APOLLO", "PHARMEASY", "NETMEDS", "1MG"),
            categoryRules(SpendCategory.HEALTH, "HOSPITAL", "PHARMACY", "MEDICAL", "MEDICINE", "CLINIC"),
            merchantRules(SpendCategory.ENTERTAINMENT, "BOOKMYSHOW"),
            categoryRules(SpendCategory.ENTERTAINMENT, "CINEMA", "MOVIE", "GAMING"),
            merchantRules(SpendCategory.TRAVEL, "MAKEMYTRIP", "GOIBIBO"),
            merchantRules(SpendCategory.TRAVEL, "CLEARTRIP", "YATRA", "INDIGO", "AIRINDIA"),
            categoryRules(SpendCategory.TRAVEL, "HOTEL", "FLIGHT", "AIRLINE", "TRAVEL"),
            categoryRules(SpendCategory.EDUCATION, "SCHOOL", "COLLEGE", "COURSE", "TUITION", "EDUCATION"),
            merchantRules(SpendCategory.SUBSCRIPTIONS, "NETFLIX", "SPOTIFY", "HOTSTAR"),
            merchantRules(SpendCategory.SUBSCRIPTIONS, "SONYLIV", "ZEE5", "YOUTUBE PREMIUM"),
            categoryRules(SpendCategory.SUBSCRIPTIONS, "SUBSCRIPTION"),
        ).flatten()

        fun merchantRules(category: SpendCategory, vararg keywords: String): List<MerchantRule> =
            keywords.map { MerchantRule(keyword = it, canonicalMerchant = it, category = category) }

        fun categoryRules(category: SpendCategory, vararg keywords: String): List<MerchantRule> =
            keywords.map { MerchantRule(keyword = it, canonicalMerchant = null, category = category) }

        data class MerchantRule(
            val keyword: String,
            val canonicalMerchant: String?,
            val category: SpendCategory,
        )

    }
}

/** Built-in category result with a canonical merchant when a brand keyword matched. */
data class MerchantCategorization(
    val merchant: String?,
    val category: SpendCategory,
)

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
