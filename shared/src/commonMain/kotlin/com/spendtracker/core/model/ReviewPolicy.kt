package com.spendtracker.core.model

/**
 * Review-state rules shared by parser, repository, and UI.
 *
 * Confidence is derived from the surviving review reasons only, so resolving the
 * category concern (the only concern a manual save can resolve) raises
 * confidence exactly when no other concern remains. This keeps the display
 * threshold `needsReview` consistent with stored reasons.
 */
object ReviewPolicy {
    /** Confidence for a set of stored reasons, mirroring parser confidence rules. */
    fun confidenceFor(reasons: Set<TransactionReviewReason>): Double = when {
        reasons.any {
            it == TransactionReviewReason.CONFLICTING_AMOUNTS ||
                it == TransactionReviewReason.CONFLICTING_DIRECTIONS
        } -> 0.35

        reasons.isNotEmpty() -> 0.60

        else -> 0.90
    }
}
