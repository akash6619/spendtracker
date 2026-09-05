package com.spendtracker.core.repository

import com.spendtracker.core.database.TransactionDao
import com.spendtracker.core.database.TransactionEntity
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
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
    private val nowEpochMillis: () -> Long,
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAll().map { rows -> rows.map(TransactionEntity::toDomain) }

    override suspend fun upsert(transactions: List<TransactionCandidate>) {
        // One timestamp makes every row in the import batch internally consistent.
        val now = nowEpochMillis()
        dao.upsertAll(transactions.map { it.toEntity(now) })
    }

    override suspend fun getById(id: String): LedgerTransaction? = dao.findById(id)?.toDomain()

    override suspend fun updateOverrides(
        id: String,
        category: SpendCategory?,
        includedInSpend: Boolean?,
    ) {
        dao.updateOverrides(id, category?.name, includedInSpend, nowEpochMillis())
    }

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
    detectedIncludedInSpend = transaction.isIncludedInSpend,
    userIncludedInSpend = null,
    createdAtEpochMillis = now,
    updatedAtEpochMillis = now,
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
    ),
    userCategory = userCategory?.let(SpendCategory::valueOf),
    userIncludedInSpend = userIncludedInSpend,
)
