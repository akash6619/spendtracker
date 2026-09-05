package com.spendtracker.core.importing

import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.repository.ImportStateRepository
import com.spendtracker.core.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs the bounded stream-to-parse-to-persist import pipeline.
 *
 * It selects an initial or overlap cutoff, processes only one raw message at a
 * time, writes accepted candidates in bounded batches, emits safe progress, and
 * durably records completion or failure. Retrying is safe because repository
 * fingerprint/provider-ID rules make every batch idempotent.
 */
class ImportCoordinator(
    private val messageSource: MessageSource,
    private val parser: FinancialMessageParser,
    private val fingerprinter: SourceFingerprinter,
    private val transactionRepository: TransactionRepository,
    private val importStateRepository: ImportStateRepository,
    private val historyCutoffProvider: HistoryCutoffProvider,
    private val nowEpochMillis: () -> Long,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val reconciliationOverlapMillis: Long = DEFAULT_OVERLAP_MILLIS,
) : ImportRunner {
    init {
        require(batchSize > 0) { "Batch size must be positive" }
        require(reconciliationOverlapMillis >= 0) { "Overlap must not be negative" }
    }

    override suspend fun run(
        mode: ImportMode,
        onProgress: (ImportProgress) -> Unit,
    ): ImportState {
        val previous = importStateRepository.getImportState()
        val attemptAt = nowEpochMillis()
        var progress = ImportProgress()
        val pending = mutableListOf<TransactionCandidate>()

        importStateRepository.saveImportState(
            previous.copy(
                status = ImportRunStatus.RUNNING,
                lastAttemptEpochMillis = attemptAt,
                progress = progress,
                failureCode = null,
            ),
        )

        suspend fun flush() {
            if (pending.isEmpty()) return
            val countBefore = transactionRepository.count()
            transactionRepository.upsert(pending.toList())
            val newlySaved = (transactionRepository.count() - countBefore).coerceAtLeast(0)
            pending.clear()
            progress = progress.copy(savedTransactions = progress.savedTransactions + newlySaved)
            onProgress(progress)
        }

        try {
            val cutoff = when (mode) {
                ImportMode.INITIAL -> historyCutoffProvider.cutoffEpochMillis()
                ImportMode.RECONCILIATION -> maxOf(
                    historyCutoffProvider.cutoffEpochMillis(),
                    (previous.lastSuccessfulScanEpochMillis ?: attemptAt) - reconciliationOverlapMillis,
                )
            }
            messageSource.readMessagesSince(cutoff) { message ->
                val parsed = parser.parse(message)
                progress = if (parsed == null) {
                    progress.copy(
                        scannedMessages = progress.scannedMessages + 1,
                        rejectedMessages = progress.rejectedMessages + 1,
                    )
                } else {
                    pending += TransactionCandidate(
                        sourceType = SourceType.ANDROID_SMS,
                        sourceProviderId = message.sourceId,
                        sourceFingerprint = fingerprinter.fingerprint(message),
                        transaction = parsed,
                    )
                    progress.copy(
                        scannedMessages = progress.scannedMessages + 1,
                        recognizedTransactions = progress.recognizedTransactions + 1,
                        reviewTransactions = progress.reviewTransactions +
                            if (parsed.confidence < REVIEW_THRESHOLD || parsed.kind == TransactionKind.UNKNOWN) 1 else 0,
                    )
                }
                onProgress(progress)
                if (pending.size >= batchSize) flush()
            }
            flush()

            return previous.copy(
                status = ImportRunStatus.COMPLETED,
                initialImportComplete = previous.initialImportComplete || mode == ImportMode.INITIAL,
                lastSuccessfulScanEpochMillis = nowEpochMillis(),
                lastAttemptEpochMillis = attemptAt,
                progress = progress,
                failureCode = null,
            ).also { importStateRepository.saveImportState(it) }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                importStateRepository.saveImportState(
                    previous.failed(attemptAt, progress, ImportFailureCode.INTERRUPTED),
                )
            }
            throw cancellation
        } catch (failure: Exception) {
            val failed = previous.failed(
                attemptAt,
                progress,
                ImportFailureCode.SOURCE_OR_STORAGE_FAILURE,
            )
            importStateRepository.saveImportState(failed)
            return failed
        }
    }

    private fun ImportState.failed(
        attemptAt: Long,
        progress: ImportProgress,
        code: ImportFailureCode,
    ): ImportState = copy(
        status = ImportRunStatus.FAILED,
        lastAttemptEpochMillis = attemptAt,
        progress = progress,
        failureCode = code,
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val DEFAULT_OVERLAP_MILLIS = 5 * 60 * 1000L
        const val REVIEW_THRESHOLD = 0.75
    }
}
