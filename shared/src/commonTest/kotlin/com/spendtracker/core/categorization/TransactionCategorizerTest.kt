package com.spendtracker.core.categorization

import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.parser.ParseOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Defines built-in category, collision-precedence, and merchant-normalization behavior.
 * The table covers every fixed MVP bucket using synthetic merchant identities and
 * locks `Other` as the fallback for unknown or missing merchants.
 */
class TransactionCategorizerTest {
    private val normalizer = MerchantNormalizer()
    private val categorizer = TransactionCategorizer()

    @Test
    fun mapsEveryMvpBucketAndFallsBackToOther() {
        val cases = listOf(
            "Northstar Cafe" to SpendCategory.FOOD_AND_DINING,
            "Northstar Grocery" to SpendCategory.GROCERIES,
            "Northstar Fuel" to SpendCategory.TRANSPORT,
            "Northstar Retail" to SpendCategory.SHOPPING,
            "Northstar Electricity" to SpendCategory.BILLS_AND_UTILITIES,
            "Northstar Rent" to SpendCategory.HOUSING,
            "Northstar Pharmacy" to SpendCategory.HEALTH,
            "Northstar Cinema" to SpendCategory.ENTERTAINMENT,
            "Northstar Hotel" to SpendCategory.TRAVEL,
            "Northstar Tuition" to SpendCategory.EDUCATION,
            "Northstar Subscription" to SpendCategory.SUBSCRIPTIONS,
            "Northstar Unknown" to SpendCategory.OTHER,
        )

        cases.forEach { (merchant, category) ->
            assertEquals(
                category,
                categorizer.categorize(TransactionKind.PURCHASE, normalizer.normalize(merchant)),
                merchant,
            )
        }
        assertEquals(SpendCategory.OTHER, categorizer.categorize(TransactionKind.PURCHASE, null))
    }

    @Test
    fun feeWinsOverMerchantKeywordCollision() {
        assertEquals(
            SpendCategory.FEES_AND_CHARGES,
            categorizer.categorize(TransactionKind.FEE, normalizer.normalize("Northstar Cafe")),
        )
    }

    @Test
    fun categorizesEmbeddedMerchantKeywordsWithInstamartPrecedence() {
        assertEquals(
            SpendCategory.GROCERIES,
            categorizer.categorize(TransactionKind.PURCHASE, normalizer.normalize("XYZINSTAMARTGR")),
        )
        assertEquals(
            SpendCategory.GROCERIES,
            categorizer.categorize(TransactionKind.PURCHASE, normalizer.normalize("SWIGGYINSTAMARTGR")),
        )
        assertEquals(
            SpendCategory.FOOD_AND_DINING,
            categorizer.categorize(TransactionKind.PURCHASE, normalizer.normalize("SWIGGYPVTLTDFOOD1")),
        )
        assertEquals(
            SpendCategory.FOOD_AND_DINING,
            categorizer.categorize(TransactionKind.PURCHASE, normalizer.normalize("XYZZOMATOXYZ")),
        )
    }

    @Test
    fun canonicalizesMerchantVariantsToMatchedKeyword() {
        val variants = listOf("SWIGGY", "SWIGGYPVTLTDFOOD1", "PAY SWIGGY ORDER")

        variants.forEach { merchant ->
            val result = categorizer.resolve(TransactionKind.PURCHASE, normalizer.normalize(merchant))
            assertEquals("SWIGGY", result.merchant, merchant)
            assertEquals(SpendCategory.FOOD_AND_DINING, result.category, merchant)
        }

        val instamart = categorizer.resolve(
            TransactionKind.PURCHASE,
            normalizer.normalize("SWIGGYINSTAMARTGR"),
        )
        assertEquals("INSTAMART", instamart.merchant)
        assertEquals(SpendCategory.GROCERIES, instamart.category)

        val unknown = categorizer.resolve(TransactionKind.PURCHASE, normalizer.normalize("Northstar Unknown"))
        assertEquals("NORTHSTAR UNKNOWN", unknown.merchant)
        assertEquals(SpendCategory.OTHER, unknown.category)

        val genericCafe = categorizer.resolve(TransactionKind.PURCHASE, normalizer.normalize("Northstar Cafe"))
        assertEquals("NORTHSTAR CAFE", genericCafe.merchant)
        assertEquals(SpendCategory.FOOD_AND_DINING, genericCafe.category)

        val genericRetail = categorizer.resolve(TransactionKind.PURCHASE, normalizer.normalize("Ratnadeep Retail"))
        assertEquals("RATNADEEP RETAIL", genericRetail.merchant)
        assertEquals(SpendCategory.SHOPPING, genericRetail.category)

        val fee = categorizer.resolve(TransactionKind.FEE, normalizer.normalize("Northstar Cafe"))
        assertEquals("NORTHSTAR CAFE", fee.merchant)
        assertEquals(SpendCategory.FEES_AND_CHARGES, fee.category)
    }

    @Test
    fun canonicalizesExpandedMerchantBrands() {
        val cases = listOf(
            Triple("JioMart Online", "JIOMART", SpendCategory.GROCERIES),
            Triple("Dominos Pizza", "DOMINOS", SpendCategory.FOOD_AND_DINING),
            Triple("Dominos Restaurant", "DOMINOS", SpendCategory.FOOD_AND_DINING),
            Triple("NammaYatri Ride", "NAMMAYATRI", SpendCategory.TRANSPORT),
            Triple("Nykaa E Retail", "NYKAA", SpendCategory.SHOPPING),
            Triple("Apollo Pharmacy", "APOLLO", SpendCategory.HEALTH),
            Triple("Cleartrip Booking", "CLEARTRIP", SpendCategory.TRAVEL),
            Triple("YouTube Premium", "YOUTUBE PREMIUM", SpendCategory.SUBSCRIPTIONS),
        )

        cases.forEach { (rawMerchant, canonicalMerchant, category) ->
            val result = categorizer.resolve(TransactionKind.PURCHASE, normalizer.normalize(rawMerchant))
            assertEquals(canonicalMerchant, result.merchant, rawMerchant)
            assertEquals(category, result.category, rawMerchant)
        }
    }

    @Test
    fun otherPurchaseIsReviewableWithoutBeingExcludedForCategoryUncertainty() {
        val outcome = assertIs<ParseOutcome.NeedsReview>(
            FinancialMessageParser().classify(
                SourceMessage("1", "SYNTHETIC", "INR 50 paid at NORTHSTAR UNKNOWN", 1_000),
            ),
        )

        assertTrue(TransactionReviewReason.UNKNOWN_CATEGORY in outcome.reasons)
        assertTrue(outcome.transaction.includedInSpend)
    }

    @Test
    fun normalizesPunctuationCasingAndCommonSuffixesWithoutRawContext() {
        assertEquals("NORTHSTAR CAFE", normalizer.normalize(" northstar-cafe pvt. ltd "))
        assertEquals("NORTHSTAR CAFE", normalizer.normalize("NORTHSTAR_CAFE@upi"))
        assertNull(normalizer.normalize("-"))
    }

    @Test
    fun merchantRuleCannotOverrideFeeAndClearsOnlyCategoryUncertainty() {
        val fee = FinancialMessageParser().parse(
            SourceMessage("1", "SYNTHETIC", "INR 50 fee debited at NORTHSTAR CAFE", 1_000),
        )!!
        assertEquals(SpendCategory.FEES_AND_CHARGES, fee.withMerchantCategory(SpendCategory.TRAVEL).category)

        val other = FinancialMessageParser().parse(
            SourceMessage("2", "SYNTHETIC", "INR 50 paid at NORTHSTAR UNKNOWN", 1_000),
        )!!
        val resolved = other.withMerchantCategory(SpendCategory.HEALTH)
        assertEquals(SpendCategory.HEALTH, resolved.category)
        assertTrue(resolved.reviewReasons.isEmpty())
        assertEquals(0.90, resolved.confidence)
    }
}
