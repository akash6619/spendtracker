package com.spendtracker.core.parser

import com.spendtracker.core.model.ParsedTransaction

/**
 * Privacy-safe explanations for messages the parser does not turn into ledger records.
 *
 * These values intentionally describe only a rule outcome. They may be counted in
 * aggregate diagnostics, persisted, or shown in summary UI because they contain no
 * sender, message text, amount, timestamp, or other source identifier.
 */
enum class RejectionReason {
    EMPTY, NON_FINANCIAL, OTP_OR_AUTHORIZATION, FAILED_OR_CANCELLED, BALANCE_ONLY,
    MISSING_AMOUNT, UNSUPPORTED_CURRENCY, MALFORMED_AMOUNT, AMOUNT_OVERFLOW, MISSING_DIRECTION,
}

/**
 * Privacy-safe explanations for accepted financial records that need confirmation.
 *
 * A review outcome still contains a deterministic best-effort transaction so the
 * user can inspect and correct it later. Reasons are deliberately coarse and safe
 * for aggregate diagnostics; raw source text never crosses this boundary.
 */
enum class ReviewReason {
    CONFLICTING_AMOUNTS, CONFLICTING_DIRECTIONS, UNKNOWN_KIND, MISSING_MERCHANT,
}

/**
 * Complete result of classifying one ephemeral source message.
 *
 * [Accepted] is safe for normal persistence, [NeedsReview] is persisted but kept
 * visibly uncertain, and [Rejected] contains no parsed transaction. This sealed
 * shape prevents callers from silently treating unsupported or ambiguous input as
 * ordinary spend while exposing only privacy-safe diagnostic reasons.
 */
sealed interface ParseOutcome {
    /** A completed event whose parsed facts have no current review flags. */
    data class Accepted(val transaction: ParsedTransaction) : ParseOutcome

    /** A completed event retained for later user confirmation with one or more safe reasons. */
    data class NeedsReview(val transaction: ParsedTransaction, val reasons: Set<ReviewReason>) : ParseOutcome

    /** An unsupported, incomplete, or non-financial message that must not enter the ledger. */
    data class Rejected(val reason: RejectionReason) : ParseOutcome
}
