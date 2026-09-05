package com.spendtracker.core.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sourceType", "sourceProviderId"], unique = true),
        Index(value = ["sourceType", "sourceFingerprint"], unique = true),
        Index(value = ["sourceReceivedAtEpochMillis"]),
        Index(value = ["detectedCategory"]),
    ],
)
/**
 * Room representation of one transaction in the local ledger.
 * It stores source identity, inferred financial fields, parser metadata, and
 * nullable user overrides. Raw SMS bodies and sender details are intentionally
 * absent from the schema.
 */
data class TransactionEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceProviderId: String?,
    val sourceFingerprint: String,
    val sourceReceivedAtEpochMillis: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val kind: String,
    val detectedCategory: String,
    val userCategory: String?,
    val merchant: String?,
    val accountHint: String?,
    val confidence: Double,
    val parserVersion: Int,
    val detectedIncludedInSpend: Boolean,
    val userIncludedInSpend: Boolean?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "import_state")
/**
 * Stores durable progress for the production import lifecycle.
 * The singleton row records completion, the last successful scan, safe counts,
 * and parser version without retaining any message content.
 */
data class ImportStateEntity(
    @PrimaryKey val id: Int = 1,
    val status: String = "NOT_STARTED",
    val initialImportComplete: Boolean = false,
    val lastScanEpochMillis: Long? = null,
    val lastAttemptEpochMillis: Long? = null,
    val lastScannedCount: Int = 0,
    val lastRecognizedCount: Int = 0,
    val lastRejectedCount: Int = 0,
    val lastReviewCount: Int = 0,
    val lastSavedCount: Int = 0,
    val parserVersion: Int = 1,
    val failureCode: String? = null,
)

@Entity(tableName = "merchant_category_rules")
/**
 * Stores a category correction explicitly approved for a normalized merchant.
 * Later categorization work can apply the rule to matching transactions while
 * per-transaction overrides continue to take higher precedence.
 */
data class MerchantCategoryRuleEntity(
    @PrimaryKey val normalizedMerchant: String,
    val category: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "settings")
/**
 * Stores local product preferences that must survive process death.
 * It currently tracks whether onboarding was seen and can grow only through
 * versioned Room migrations.
 */
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val onboardingSeen: Boolean = false,
    val smsPermissionRequested: Boolean = false,
)

/**
 * Read-only query result containing one category's included INR spend.
 * Amounts remain in minor units, and `transactionCount` supports explainable UI.
 */
data class CategoryTotal(
    val category: String,
    val totalMinor: Long,
    val transactionCount: Int,
)

/**
 * Read-only query result for included INR spend within an explicit time range.
 * It provides both the exact total and contributing record count.
 */
data class PeriodTotal(
    val totalMinor: Long,
    val transactionCount: Int,
)
