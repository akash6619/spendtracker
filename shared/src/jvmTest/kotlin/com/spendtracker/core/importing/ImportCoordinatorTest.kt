package com.spendtracker.core.importing

import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.parser.RejectionReason
import com.spendtracker.core.repository.ImportStateRepository
import com.spendtracker.core.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises import orchestration independently of Android and Room.
 * Synthetic streaming sources and recording repositories verify boundaries,
 * counters, bounded batching, cancellation, retry, and reconciliation overlap.
 */
class ImportCoordinatorTest {
    @Test
    fun initialImportUsesCutoffStreamsProgressAndWritesBoundedBatches() = runTest {
        val source = GeneratedMessageSource(total = 205, rejectedEvery = 10)
        val transactions = RecordingTransactionRepository()
        val states = FakeImportStateRepository()
        val progressUpdates = mutableListOf<ImportProgress>()
        val coordinator = coordinator(source, transactions, states, historyCutoff = 1234, batchSize = 25)

        val result = coordinator.run(ImportMode.INITIAL, progressUpdates::add)

        assertEquals(1234, source.lastCutoff)
        assertEquals(205, result.progress.scannedMessages)
        assertEquals(184, result.progress.recognizedTransactions)
        assertEquals(21, result.progress.rejectedMessages)
        assertEquals(21, result.progress.rejectionReasonCounts[RejectionReason.NON_FINANCIAL])
        assertEquals(184, result.progress.savedTransactions)
        assertTrue(result.initialImportComplete)
        assertEquals(ImportRunStatus.COMPLETED, result.status)
        assertEquals(2, result.parserVersion)
        assertTrue(transactions.maxBatchSize <= 25)
        assertEquals(result.progress, progressUpdates.last())
    }

    @Test
    fun retryAfterPartialFailureIsIdempotent() = runTest {
        val transactions = RecordingTransactionRepository()
        val states = FakeImportStateRepository()
        var shouldFail = true
        val source = object : MessageSource {
            override suspend fun readMessagesSince(
                cutoffEpochMillis: Long,
                consume: suspend (SourceMessage) -> Unit,
            ): Int {
                consume(message("1"))
                if (shouldFail) error("Synthetic source failure")
                consume(message("2"))
                return 2
            }
        }
        val coordinator = coordinator(source, transactions, states, batchSize = 1)

        val failed = coordinator.run(ImportMode.INITIAL) {}
        assertEquals(ImportRunStatus.FAILED, failed.status)
        assertEquals(1, transactions.count())

        shouldFail = false
        val completed = coordinator.run(ImportMode.INITIAL) {}
        assertEquals(ImportRunStatus.COMPLETED, completed.status)
        assertEquals(2, transactions.count())
        assertEquals(1, completed.progress.savedTransactions)
    }

    @Test
    fun cancellationPersistsRecoverableFailureAndRethrows() = runTest {
        val states = FakeImportStateRepository()
        val source = object : MessageSource {
            override suspend fun readMessagesSince(
                cutoffEpochMillis: Long,
                consume: suspend (SourceMessage) -> Unit,
            ): Int {
                consume(message("1"))
                throw CancellationException("Synthetic cancellation")
            }
        }
        val coordinator = coordinator(source, RecordingTransactionRepository(), states)

        assertFailsWith<CancellationException> { coordinator.run(ImportMode.INITIAL) {} }

        assertEquals(ImportRunStatus.FAILED, states.getImportState().status)
        assertEquals(ImportFailureCode.INTERRUPTED, states.getImportState().failureCode)
        assertEquals(1, states.getImportState().progress.scannedMessages)
    }

    @Test
    fun reconciliationUsesLastSuccessWithOverlapAndNeverExceedsHistoryBoundary() = runTest {
        val source = GeneratedMessageSource(0)
        val states = FakeImportStateRepository(
            ImportState(
                status = ImportRunStatus.COMPLETED,
                initialImportComplete = true,
                lastSuccessfulScanEpochMillis = 20_000,
            ),
        )
        val coordinator = coordinator(
            source = source,
            transactions = RecordingTransactionRepository(),
            states = states,
            historyCutoff = 5_000,
            overlap = 2_000,
        )

        coordinator.run(ImportMode.RECONCILIATION) {}

        assertEquals(18_000, source.lastCutoff)
    }

    @Test
    fun tenThousandMessagesNeverExceedConfiguredBatch() = runTest {
        val transactions = RecordingTransactionRepository()
        val result = coordinator(
            source = GeneratedMessageSource(10_000),
            transactions = transactions,
            states = FakeImportStateRepository(),
            batchSize = 100,
        ).run(ImportMode.INITIAL) {}

        assertEquals(10_000, result.progress.scannedMessages)
        assertEquals(100, transactions.maxBatchSize)
        assertEquals(10_000, transactions.count())
    }

    private fun coordinator(
        source: MessageSource,
        transactions: RecordingTransactionRepository,
        states: FakeImportStateRepository,
        historyCutoff: Long = 1_000,
        batchSize: Int = 100,
        overlap: Long = 300_000,
    ) = ImportCoordinator(
        messageSource = source,
        parser = FinancialMessageParser(),
        fingerprinter = SourceFingerprinter { it.sourceId ?: "missing" },
        transactionRepository = transactions,
        importStateRepository = states,
        historyCutoffProvider = HistoryCutoffProvider { historyCutoff },
        nowEpochMillis = { 30_000 },
        batchSize = batchSize,
        reconciliationOverlapMillis = overlap,
    )

    private fun message(id: String) = SourceMessage(
        sourceId = id,
        sender = "SYNTHETIC",
        body = "INR 12.00 debited at Northstar Cafe",
        receivedAtEpochMillis = 2_000,
    )
}

/**
 * Generates synthetic messages lazily without retaining a source corpus.
 * This lets the high-volume test detect an unbounded coordinator batch while
 * keeping raw message lifetime equivalent to the production source callback.
 */
private class GeneratedMessageSource(
    private val total: Int,
    private val rejectedEvery: Int? = null,
) : MessageSource {
    var lastCutoff: Long? = null

    override suspend fun readMessagesSince(
        cutoffEpochMillis: Long,
        consume: suspend (SourceMessage) -> Unit,
    ): Int {
        lastCutoff = cutoffEpochMillis
        repeat(total) { index ->
            val rejected = rejectedEvery?.let { index % it == 0 } == true
            consume(
                SourceMessage(
                    sourceId = index.toString(),
                    sender = "SYNTHETIC",
                    body = if (rejected) "Synthetic non-financial notice" else {
                        "INR 12.00 debited at Northstar Cafe"
                    },
                    receivedAtEpochMillis = cutoffEpochMillis + index,
                ),
            )
        }
        return total
    }
}

/**
 * Records only deduplication keys and maximum write size for coordinator tests.
 * It deliberately avoids storing parsed records because these tests measure
 * orchestration and batch bounds rather than repository mapping.
 */
private class RecordingTransactionRepository : TransactionRepository {
    private val fingerprints = linkedSetOf<String>()
    var maxBatchSize = 0

    override fun observeTransactions(): Flow<List<LedgerTransaction>> = MutableStateFlow(emptyList())
    override suspend fun upsert(transactions: List<TransactionCandidate>) {
        maxBatchSize = maxOf(maxBatchSize, transactions.size)
        fingerprints += transactions.map { it.sourceFingerprint }
    }
    override suspend fun getById(id: String): LedgerTransaction? = null
    override suspend fun updateOverrides(id: String, category: SpendCategory?, includedInSpend: Boolean?) = Unit
    override suspend fun count(): Int = fingerprints.size
    override suspend fun clear() = fingerprints.clear()
}

/**
 * Keeps import and permission state observable without database setup.
 * Immediate StateFlow emission mirrors the contract used by the production
 * ViewModel while allowing tests to inspect every durable terminal state.
 */
private class FakeImportStateRepository(
    initial: ImportState = ImportState(),
) : ImportStateRepository {
    private val state = MutableStateFlow(initial)
    private var permissionRequested = false

    override fun observeImportState(): Flow<ImportState> = state
    override suspend fun getImportState(): ImportState = state.value
    override suspend fun saveImportState(state: ImportState) { this.state.value = state }
    override suspend fun wasSmsPermissionRequested(): Boolean = permissionRequested
    override suspend fun setSmsPermissionRequested(requested: Boolean) { permissionRequested = requested }
}
