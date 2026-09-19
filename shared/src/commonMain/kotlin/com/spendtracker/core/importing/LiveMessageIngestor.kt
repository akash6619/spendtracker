package com.spendtracker.core.importing

import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.parser.ParseOutcome
import com.spendtracker.core.repository.TransactionRepository

/**
 * Reconciles a narrow recent-message window after an Android notification signal.
 *
 * Raw message bodies remain inside the source callback only long enough to parse
 * and fingerprint them. The returned list contains only transactions that were
 * absent before this run, allowing Android to alert once after durable storage.
 */
class LiveMessageIngestor(
    private val messageSource: MessageSource,
    private val parser: FinancialMessageParser,
    private val fingerprinter: SourceFingerprinter,
    private val transactionRepository: TransactionRepository,
) {
    suspend fun ingestSince(cutoffEpochMillis: Long): List<LedgerTransaction> {
        val saved = mutableListOf<LedgerTransaction>()
        messageSource.readMessagesSince(cutoffEpochMillis) { message ->
            val parsed = when (val outcome = parser.classify(message)) {
                is ParseOutcome.Accepted -> outcome.transaction
                is ParseOutcome.NeedsReview -> outcome.transaction
                is ParseOutcome.Rejected -> null
            } ?: return@readMessagesSince

            val fingerprint = fingerprinter.fingerprint(message)
            saved += transactionRepository.upsertAndGetInserted(
                listOf(
                    TransactionCandidate(
                        sourceType = SourceType.ANDROID_SMS,
                        sourceProviderId = message.sourceId,
                        sourceFingerprint = fingerprint,
                        transaction = parsed,
                    ),
                ),
            )
        }
        return saved
    }
}
