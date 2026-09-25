package com.spendtracker.core.repository

import com.spendtracker.core.categorization.MerchantNormalizer
import com.spendtracker.core.categorization.TransactionCategorizer
import com.spendtracker.core.categorization.withMerchantCategory
import com.spendtracker.core.database.MerchantCategoryRuleDao
import com.spendtracker.core.database.MerchantCategoryRuleEntity
import com.spendtracker.core.database.TransactionDao
import com.spendtracker.core.database.TransactionEntity
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.MerchantCategoryRule
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ReviewPolicy
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
    private val categorizer: TransactionCategorizer = TransactionCategorizer(),
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAll().map { rows -> rows.map(TransactionEntity::toDomain) }

    override suspend fun upsert(transactions: List<TransactionCandidate>) {
        persist(transactions)
    }

    override suspend fun upsertAndGetInserted(
        transactions: List<TransactionCandidate>,
    ): List<LedgerTransaction> {
        val insertedIds = persist(transactions)
        return insertedIds.mapNotNull { dao.findById(it)?.toDomain() }
    }

    private suspend fun persist(transactions: List<TransactionCandidate>): List<String> {
        // One timestamp makes every row in the import batch internally consistent.
        val now = nowEpochMillis()
        val merchantRules = canonicalizeRules(merchantRuleDao.getAll())
            .associate { it.normalizedMerchant to SpendCategory.valueOf(it.category) }
        return dao.upsertAll(transactions.map { candidate ->
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

    override suspend fun getByFingerprint(sourceType: SourceType, fingerprint: String): LedgerTransaction? =
        dao.findByFingerprint(sourceType.name, fingerprint)?.toDomain()

    override suspend fun updateTransaction(
        id: String,
        merchant: String?,
        kind: TransactionKind,
        direction: TransactionDirection,
        category: SpendCategory,
        includedInSpend: Boolean,
    ) {
        val current = dao.findById(id) ?: return
        val storedReasons = current.reviewReasons.takeIf(String::isNotBlank)
            ?.split(',')
            ?.mapTo(linkedSetOf(), TransactionReviewReason::valueOf)
            ?: emptySet()
        // Explicit type, direction, and category choices resolve their concerns;
        // unrelated amount ambiguity survives and controls the confidence value.
        val resolvedReasons = storedReasons - setOf(
            TransactionReviewReason.UNKNOWN_CATEGORY,
            TransactionReviewReason.CONFLICTING_DIRECTIONS,
            TransactionReviewReason.UNKNOWN_KIND,
        )
        dao.updateTransaction(
            id = id,
            merchant = merchant?.trim()?.takeIf(String::isNotEmpty),
            kind = kind.name,
            direction = direction.name,
            category = category.name,
            included = includedInSpend,
            reviewReasons = resolvedReasons.sorted().joinToString(","),
            confidence = ReviewPolicy.confidenceFor(resolvedReasons),
            updatedAt = nowEpochMillis(),
        )
    }

    override fun observeMerchantRules(): Flow<List<MerchantCategoryRule>> =
        merchantRuleDao.observeAll().map { rules ->
            canonicalizeRules(rules).map(MerchantCategoryRuleEntity::toDomain)
        }

    override suspend fun saveMerchantRule(merchant: String, category: SpendCategory) {
        val normalized = requireNotNull(merchantNormalizer.normalize(merchant)) {
            "Merchant must contain at least two letters or digits"
        }
        val canonical = requireNotNull(categorizer.canonicalizeMerchant(normalized))
        val now = nowEpochMillis()
        val matchingRules = merchantRuleDao.getAll().filter {
            categorizer.canonicalizeMerchant(it.normalizedMerchant) == canonical
        }
        val existing = matchingRules.maxByOrNull(MerchantCategoryRuleEntity::updatedAtEpochMillis)
        matchingRules.filterNot { it.normalizedMerchant == canonical }.forEach {
            merchantRuleDao.delete(it.normalizedMerchant)
        }
        merchantRuleDao.save(
            MerchantCategoryRuleEntity(
                normalizedMerchant = canonical,
                category = category.name,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun deleteMerchantRule(merchant: String) {
        val normalized = merchantNormalizer.normalize(merchant) ?: return
        val canonical = categorizer.canonicalizeMerchant(normalized) ?: return
        merchantRuleDao.getAll()
            .filter { categorizer.canonicalizeMerchant(it.normalizedMerchant) == canonical }
            .forEach { merchantRuleDao.delete(it.normalizedMerchant) }
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun clear() = dao.deleteAll()

    private fun canonicalizeRules(rules: List<MerchantCategoryRuleEntity>): List<MerchantCategoryRuleEntity> =
        rules
            .sortedBy(MerchantCategoryRuleEntity::updatedAtEpochMillis)
            .associateBy { rule -> categorizer.canonicalizeMerchant(rule.normalizedMerchant) ?: rule.normalizedMerchant }
            .map { (canonicalMerchant, rule) -> rule.copy(normalizedMerchant = canonicalMerchant) }
            .sortedBy(MerchantCategoryRuleEntity::normalizedMerchant)
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
    category = transaction.category.name,
    merchant = transaction.merchant,
    accountHint = transaction.accountHint,
    confidence = transaction.confidence,
    parserVersion = transaction.parserVersion,
    reviewReasons = transaction.reviewReasons.map(TransactionReviewReason::name).sorted().joinToString(","),
    includedInSpend = transaction.includedInSpend,
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
    sourceReceivedAtEpochMillis = sourceReceivedAtEpochMillis,
    money = Money(amountMinor, CurrencyCode.valueOf(currency)),
    direction = TransactionDirection.valueOf(direction),
    kind = TransactionKind.valueOf(kind),
    category = SpendCategory.valueOf(category),
    merchant = merchant,
    accountHint = accountHint,
    confidence = confidence,
    parserVersion = parserVersion,
    reviewReasons = reviewReasons.takeIf(String::isNotBlank)
        ?.split(',')
        ?.mapTo(linkedSetOf(), TransactionReviewReason::valueOf)
        ?: emptySet(),
    includedInSpend = includedInSpend,
    userEdited = userEdited,
)
