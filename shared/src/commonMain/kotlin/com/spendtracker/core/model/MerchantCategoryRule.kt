package com.spendtracker.core.model

/**
 * User-approved category mapping for one normalized merchant identity.
 *
 * The normalized key contains no raw SMS body or sender. Repository imports use
 * this mapping ahead of built-in categorization, while an explicit transaction
 * override remains the final authority.
 */
data class MerchantCategoryRule(
    val normalizedMerchant: String,
    val category: SpendCategory,
)
