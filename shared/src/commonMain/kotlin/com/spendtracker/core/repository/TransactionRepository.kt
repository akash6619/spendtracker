package com.spendtracker.core.repository

import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.MerchantCategoryRule
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing source of truth for the local transaction ledger.
 *
 * It exposes observable records without Room types, accepts privacy-safe import
 * candidates, supports single-value field updates, and can erase stored
 * transactions. Implementations must preserve uniqueness during every re-import.
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<LedgerTransaction>>

    suspend fun upsert(transactions: List<TransactionCandidate>)

    suspend fun getById(id: String): LedgerTransaction?

    suspend fun updateTransaction(
        id: String,
        merchant: String?,
        kind: TransactionKind,
        direction: TransactionDirection,
        category: SpendCategory,
        includedInSpend: Boolean,
    )

    fun observeMerchantRules(): Flow<List<MerchantCategoryRule>>

    suspend fun saveMerchantRule(merchant: String, category: SpendCategory)

    suspend fun deleteMerchantRule(merchant: String)

    suspend fun count(): Int

    suspend fun clear()
}
