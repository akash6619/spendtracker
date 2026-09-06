package com.spendtracker.core.model

/**
 * Composable ledger filters using stored facts.
 * Date bounds are epoch milliseconds, inclusive at the start and exclusive at
 * the end; Android converts calendar dates using the current device time zone.
 * Null values leave a dimension unrestricted. Currency never changes inclusion.
 * [foreignOnly] keeps every non-INR currency record, for dashboard deep links.
 */
data class TransactionFilter(
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null,
    val category: SpendCategory? = null,
    val included: Boolean? = null,
    val needsReview: Boolean? = null,
    val currency: CurrencyCode? = null,
    val foreignOnly: Boolean = false,
) {
    fun matches(row: LedgerTransaction): Boolean =
        (fromInclusive == null || row.sourceReceivedAtEpochMillis >= fromInclusive) &&
            (toExclusive == null || row.sourceReceivedAtEpochMillis < toExclusive) &&
            (category == null || row.category == category) &&
            (included == null || row.includedInSpend == included) &&
            (needsReview == null || row.needsReview == needsReview) &&
            (currency == null || row.money.currency == currency) &&
            (!foreignOnly || row.money.currency != CurrencyCode.INR)
}

/**
 * Retains parser concerns even after a user edit changes fields.
 * A missing merchant is deliberately not a review concern: merchant detection is
 * narrow by design and solved separately. Rows whose only stored reason is
 * MISSING_MERCHANT (including older imports whose confidence reflected it) are
 * therefore excluded from review. An unknown kind is covered by the
 * UNKNOWN_KIND reason, so the kind is not checked here a second time.
 */
val LedgerTransaction.needsReview: Boolean
    get() {
        val reasons = reviewReasons
        if (reasons.isNotEmpty() && reasons.all { it == TransactionReviewReason.MISSING_MERCHANT }) {
            return false
        }
        return reasons.filterNot { it == TransactionReviewReason.MISSING_MERCHANT }.isNotEmpty() ||
            confidence < 0.8
    }

/** Stable tie-breaking prevents rows jumping when timestamps are identical. */
fun List<LedgerTransaction>.filteredBy(filter: TransactionFilter): List<LedgerTransaction> =
    filter(filter::matches).sortedWith(
        compareByDescending<LedgerTransaction> { it.sourceReceivedAtEpochMillis }
            .thenBy { it.id },
    )
