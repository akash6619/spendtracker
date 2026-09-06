package com.spendtracker.core.model

/**
 * Ephemeral message data passed from a platform reader to the shared parser.
 * The body exists only during parsing and fingerprinting; it must never be
 * written to the SpendTracker database or application logs.
 */
data class SourceMessage(
    val sourceId: String?,
    val sender: String,
    val body: String,
    val receivedAtEpochMillis: Long,
)

/**
 * Represents an exact, non-negative monetary value in currency minor units.
 * For example, INR 10.50 is stored as `1050`, avoiding floating-point rounding
 * errors when transactions are aggregated.
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amountMinor >= 0) { "Money amount cannot be negative" }
    }
}

/**
 * Currencies currently recognized by the deterministic parser.
 * `fractionDigits` controls exact text-to-minor-unit conversion and formatting;
 * different currencies are never combined in a single aggregate.
 */
enum class CurrencyCode(val fractionDigits: Int) {
    INR(2),
    USD(2),
    EUR(2),
    GBP(2),
    JPY(0),
}

/**
 * Indicates whether a transaction moved money out of or into an account.
 * Direction is stored separately from amount so monetary values remain
 * non-negative and inclusion rules stay explicit.
 */
enum class TransactionDirection {
    DEBIT,
    CREDIT,
}

/**
 * Classifies what financial event occurred, independently of category.
 * The kind determines default spend inclusion: purchase and fee debits count,
 * while transfers, withdrawals, refunds, and credits remain visible but excluded.
 */
enum class TransactionKind {
    PURCHASE,
    TRANSFER,
    CASH_WITHDRAWAL,
    REFUND,
    FEE,
    UNKNOWN,
}

/**
 * Fixed MVP buckets used to group transactions and build category reports.
 * Every parsed transaction receives one detected value, with [OTHER] acting as
 * the deterministic fallback until the user supplies an override.
 */
enum class SpendCategory {
    FOOD_AND_DINING,
    GROCERIES,
    TRANSPORT,
    SHOPPING,
    BILLS_AND_UTILITIES,
    HOUSING,
    HEALTH,
    ENTERTAINMENT,
    TRAVEL,
    EDUCATION,
    SUBSCRIPTIONS,
    FEES_AND_CHARGES,
    OTHER,
}

/**
 * Durable, privacy-safe reasons that a parsed transaction needs user confirmation.
 *
 * Values describe only detected ambiguity and contain no SMS text or identifiers.
 * Persisting them lets category rules resolve category-only uncertainty without
 * accidentally hiding unrelated amount, direction, kind, or merchant concerns.
 */
enum class TransactionReviewReason {
    CONFLICTING_AMOUNTS,
    CONFLICTING_DIRECTIONS,
    UNKNOWN_KIND,
    UNKNOWN_CATEGORY,
    MISSING_MERCHANT,
}

/**
 * Structured financial fields inferred from one recognized source message.
 *
 * This model contains no raw body or sender. It records the parser's detected
 * values, confidence, version, and detected inclusion so results can later be
 * reviewed or reparsed without Room reads changing their spend semantics.
 */
data class ParsedTransaction(
    val sourceId: String?,
    val sourceReceivedAtEpochMillis: Long,
    val money: Money,
    val direction: TransactionDirection,
    val kind: TransactionKind,
    val category: SpendCategory,
    val merchant: String?,
    val accountHint: String?,
    val confidence: Double,
    val parserVersion: Int,
    val detectedIncludedInSpend: Boolean = direction == TransactionDirection.DEBIT &&
        kind in setOf(TransactionKind.PURCHASE, TransactionKind.FEE),
    val reviewReasons: Set<TransactionReviewReason> = emptySet(),
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1" }
    }

    /** Uses the parser's durable detected policy; a later user override takes precedence. */
    val isIncludedInSpend: Boolean
        get() = detectedIncludedInSpend
}

/**
 * Identifies the platform source that produced a transaction.
 * Only Android SMS exists today, but the explicit type prevents identifiers
 * from different future sources from sharing the same uniqueness namespace.
 */
enum class SourceType {
    ANDROID_SMS,
}

/**
 * Carries parsed fields and privacy-safe source identity into persistence.
 * The provider ID supports source lookup when still available, while the keyed
 * fingerprint provides durable deduplication without storing message content.
 */
data class TransactionCandidate(
    val sourceType: SourceType,
    val sourceProviderId: String?,
    val sourceFingerprint: String,
    val transaction: ParsedTransaction,
)

/**
 * Domain representation of a transaction stored in the local ledger.
 *
 * It combines detected fields with nullable user corrections. Consumers should
 * use [effectiveCategory] and [isIncludedInSpend] so explicit user choices always
 * take precedence and survive later imports or parser updates.
 */
data class LedgerTransaction(
    val id: String,
    val sourceType: SourceType,
    val sourceProviderId: String?,
    val sourceFingerprint: String,
    val transaction: ParsedTransaction,
    val userCategory: SpendCategory? = null,
    val userIncludedInSpend: Boolean? = null,
) {
    /** User corrections always take precedence over detected defaults. */
    val effectiveCategory: SpendCategory
        get() = userCategory ?: transaction.category

    val isIncludedInSpend: Boolean
        get() = userIncludedInSpend ?: transaction.isIncludedInSpend
}
