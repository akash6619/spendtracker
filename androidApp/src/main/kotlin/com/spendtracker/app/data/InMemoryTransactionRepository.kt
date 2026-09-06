package com.spendtracker.app.data

import com.spendtracker.core.categorization.MerchantNormalizer
import com.spendtracker.core.categorization.withMerchantCategory
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.MerchantCategoryRule
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lightweight repository used by debug demo mode and local ViewModel tests.
 *
 * It exposes records through [StateFlow] and mirrors the production repository's
 * fingerprint deduplication, stable ordering, and user-override preservation.
 * Its contents intentionally disappear with the process.
 */
class InMemoryTransactionRepository(
    initialTransactions: List<TransactionCandidate> = emptyList(),
) : TransactionRepository {
    // Debug/test substitute that mirrors Room's fingerprint and override semantics.
    private val transactions = MutableStateFlow(deduplicate(initialTransactions.map(::toLedger)))
    private val merchantRules = MutableStateFlow<List<MerchantCategoryRule>>(emptyList())
    private val merchantNormalizer = MerchantNormalizer()

    override fun observeTransactions(): Flow<List<LedgerTransaction>> =
        transactions.asStateFlow()

    override suspend fun upsert(transactions: List<TransactionCandidate>) {
        this.transactions.update { current ->
            val currentByFingerprint = current.associateBy { it.sourceFingerprint }
            val rulesByMerchant = merchantRules.value.associate { it.normalizedMerchant to it.category }
            deduplicate(current + transactions.map { candidate ->
                val existing = currentByFingerprint[candidate.sourceFingerprint]
                val normalizedMerchant = merchantNormalizer.normalize(candidate.transaction.merchant)
                toLedger(candidate.copy(
                    transaction = candidate.transaction.copy(
                        merchant = normalizedMerchant,
                    ).withMerchantCategory(normalizedMerchant?.let(rulesByMerchant::get)),
                )).copy(
                    id = existing?.id ?: toLedger(candidate).id,
                    userCategory = existing?.userCategory,
                    userIncludedInSpend = existing?.userIncludedInSpend,
                )
            })
        }
    }

    override suspend fun getById(id: String): LedgerTransaction? =
        transactions.value.firstOrNull { it.id == id }

    override suspend fun updateOverrides(id: String, category: SpendCategory?, includedInSpend: Boolean?) {
        transactions.update { rows ->
            rows.map { row ->
                if (row.id == id) row.copy(userCategory = category, userIncludedInSpend = includedInSpend)
                else row
            }
        }
    }

    override fun observeMerchantRules(): Flow<List<MerchantCategoryRule>> = merchantRules.asStateFlow()

    override suspend fun saveMerchantRule(merchant: String, category: SpendCategory) {
        val normalized = requireNotNull(merchantNormalizer.normalize(merchant))
        merchantRules.update { rules ->
            (rules.filterNot { it.normalizedMerchant == normalized } + MerchantCategoryRule(normalized, category))
                .sortedBy { it.normalizedMerchant }
        }
    }

    override suspend fun deleteMerchantRule(merchant: String) {
        val normalized = merchantNormalizer.normalize(merchant) ?: return
        merchantRules.update { rules -> rules.filterNot { it.normalizedMerchant == normalized } }
    }

    override suspend fun count(): Int = transactions.value.size

    override suspend fun clear() {
        transactions.value = emptyList()
        merchantRules.value = emptyList()
    }

    private companion object {
        fun deduplicate(transactions: List<LedgerTransaction>): List<LedgerTransaction> =
            transactions
                .associateBy { it.sourceFingerprint }
                .values
                .sortedWith(
                    compareByDescending<LedgerTransaction> { it.transaction.sourceReceivedAtEpochMillis }
                        .thenBy { it.id },
                )

        fun toLedger(candidate: TransactionCandidate) = LedgerTransaction(
            id = "${candidate.sourceType}:${candidate.sourceFingerprint}",
            sourceType = candidate.sourceType,
            sourceProviderId = candidate.sourceProviderId,
            sourceFingerprint = candidate.sourceFingerprint,
            transaction = candidate.transaction,
        )
    }
}
