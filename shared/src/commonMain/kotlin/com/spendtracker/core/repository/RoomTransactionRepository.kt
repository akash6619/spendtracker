package com.spendtracker.core.repository

import com.spendtracker.core.categorization.MerchantNormalizer
import com.spendtracker.core.categorization.withMerchantCategory
import com.spendtracker.core.database.MerchantCategoryRuleDao
import com.spendtracker.core.database.MerchantCategoryRuleEntity
import com.spendtracker.core.database.TransactionDao
import com.spendtracker.core.database.TransactionEntity
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.MerchantCategoryRule
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionReviewReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Production [TransactionRepository] backed by the shared Room DAO.
 *
 * It maps between storage and domain models, timestamps each import batch, and
 * delegates atomic deduplication to [TransactionDao]. This keeps SQL, enum
 * serialization, and persistence bookkeeping out of UI and import callers.
 */
class RoomTransactionRepository(
    private val dao: TransactionDao,
    private val merchantRuleDao: MerchantCategoryRuleDao,
    private val nowEpochMillis: () -> Long,
    private val merchantNormalizer: MerchantNormalizer = MerchantNormalizer(),
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAll().map { rows -> rows.map(TransactionEntity::toDomain) }

    override suspend fun upsert(transactions: List<TransactionCandidate>) {
        // One timestamp makes every row in the import batch internally consistent.
        val now = nowEpochMillis()
        val merchantRules = merchantRuleDao.getAll().associate { it.normalizedMerchant to SpendCategory.valueOf(it.category) }
        dao.upsertAll(transactions.map { candidate ->
            val normalizedMerchant = merchantNormalizer.normalize(candidate.transaction.merchant)
            val normalizedCandidate = candidate.copy(
                transaction = candidate.transaction.copy(merchant = normalizedMerchant),
            )
            val categorizedCandidate = normalizedCandidate.copy(
                transaction = normalizedCandidate.transaction.withMerchantCategory(
                    normalizedMerchant?.let(merchantRules::get),
                ),
            )
            categorizedCandidate.toEntity(now)
        })
    }

    override suspend fun getById(id: String): LedgerTransaction? = dao.findById(id)?.toDomain()

    override suspend fun updateOverrides(
        id: String,
        category: SpendCategory?,
        includedInSpend: Boolean?,
    ) {
        dao.updateOverrides(id, category?.name, includedInSpend, nowEpochMillis())
    }

    override fun observeMerchantRules(): Flow<List<MerchantCategoryRule>> =
        merchantRuleDao.observeAll().map { rules -> rules.map(MerchantCategoryRuleEntity::toDomain) }

    override suspend fun saveMerchantRule(merchant: String, category: SpendCategory) {
        val normalized = requireNotNull(merchantNormalizer.normalize(merchant)) {
            "Merchant must contain at least two letters or digits"
        }
        val now = nowEpochMillis()
        val existing = merchantRuleDao.find(normalized)
        merchantRuleDao.save(
            MerchantCategoryRuleEntity(
                normalizedMerchant = normalized,
                category = category.name,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun deleteMerchantRule(merchant: String) {
        merchantNormalizer.normalize(merchant)?.let { merchantRuleDao.delete(it) }
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun clear() = dao.deleteAll()
}

private fun TransactionCandidate.toEntity(now: Long): TransactionEntity = TransactionEntity(
    // The keyed fingerprint is stable locally and contains no recoverable SMS text.
    id = "${sourceType.name}:${sourceFingerprint}",
    sourceType = sourceType.name,
    sourceProviderId = sourceProviderId,
    sourceFingerprint = sourceFingerprint,
    sourceReceivedAtEpochMillis = transaction.sourceReceivedAtEpochMillis,
    amountMinor = transaction.money.amountMinor,
    currency = transaction.money.currency.name,
    direction = transaction.direction.name,
    kind = transaction.kind.name,
    detectedCategory = transaction.category.name,
    userCategory = null,
    merchant = transaction.merchant,
    accountHint = transaction.accountHint,
    confidence = transaction.confidence,
    parserVersion = transaction.parserVersion,
    reviewReasons = transaction.reviewReasons.map(TransactionReviewReason::name).sorted().joinToString(","),
    detectedIncludedInSpend = transaction.detectedIncludedInSpend,
    userIncludedInSpend = null,
    createdAtEpochMillis = now,
    updatedAtEpochMillis = now,
)

private fun MerchantCategoryRuleEntity.toDomain() = MerchantCategoryRule(
    normalizedMerchant = normalizedMerchant,
    category = SpendCategory.valueOf(category),
)

private fun TransactionEntity.toDomain(): LedgerTransaction = LedgerTransaction(
    id = id,
    sourceType = SourceType.valueOf(sourceType),
    sourceProviderId = sourceProviderId,
    sourceFingerprint = sourceFingerprint,
    transaction = ParsedTransaction(
        sourceId = sourceProviderId,
        sourceReceivedAtEpochMillis = sourceReceivedAtEpochMillis,
        money = Money(amountMinor, CurrencyCode.valueOf(currency)),
        direction = TransactionDirection.valueOf(direction),
        kind = TransactionKind.valueOf(kind),
        category = SpendCategory.valueOf(detectedCategory),
        merchant = merchant,
        accountHint = accountHint,
        confidence = confidence,
        parserVersion = parserVersion,
        reviewReasons = reviewReasons.takeIf(String::isNotBlank)
            ?.split(',')
            ?.mapTo(linkedSetOf(), TransactionReviewReason::valueOf)
            ?: emptySet(),
        detectedIncludedInSpend = detectedIncludedInSpend,
    ),
    userCategory = userCategory?.let(SpendCategory::valueOf),
    userIncludedInSpend = userIncludedInSpend,
)
