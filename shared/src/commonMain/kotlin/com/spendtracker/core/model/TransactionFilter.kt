package com.spendtracker.core.model

/**
 * Composable ledger filters using stored facts and effective user overrides.
 * Date bounds are epoch milliseconds, inclusive at the start and exclusive at
 * the end; Android converts calendar dates using the current device time zone.
 * Null values leave a dimension unrestricted. Currency never changes inclusion.
 */
data class TransactionFilter(
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null,
    val category: SpendCategory? = null,
    val included: Boolean? = null,
    val needsReview: Boolean? = null,
    val currency: CurrencyCode? = null,
) {
    fun matches(row: LedgerTransaction): Boolean =
        (fromInclusive == null || row.transaction.sourceReceivedAtEpochMillis >= fromInclusive) &&
            (toExclusive == null || row.transaction.sourceReceivedAtEpochMillis < toExclusive) &&
            (category == null || row.effectiveCategory == category) &&
            (included == null || row.isIncludedInSpend == included) &&
            (needsReview == null || row.needsReview == needsReview) &&
            (currency == null || row.transaction.money.currency == currency)
}

/** Retains parser concerns even after overrides; an override is not a full audit. */
val LedgerTransaction.needsReview: Boolean
    get() = transaction.reviewReasons.isNotEmpty() || transaction.confidence < 0.8 ||
        transaction.kind == TransactionKind.UNKNOWN

/** Stable tie-breaking prevents rows jumping when timestamps are identical. */
fun List<LedgerTransaction>.filteredBy(filter: TransactionFilter): List<LedgerTransaction> =
    filter(filter::matches).sortedWith(
        compareByDescending<LedgerTransaction> { it.transaction.sourceReceivedAtEpochMillis }
            .thenBy { it.id },
    )
