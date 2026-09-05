package com.spendtracker.core.repository

import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing source of truth for the local transaction ledger.
 *
 * It exposes observable records without Room types, accepts privacy-safe import
 * candidates, supports detail and user-override updates, and can erase stored
 * transactions. Implementations must preserve overrides during every re-import.
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<LedgerTransaction>>

    suspend fun upsert(transactions: List<TransactionCandidate>)

    suspend fun getById(id: String): LedgerTransaction?

    suspend fun updateOverrides(
        id: String,
        category: SpendCategory?,
        includedInSpend: Boolean?,
    )

    suspend fun count(): Int

    suspend fun clear()
}
