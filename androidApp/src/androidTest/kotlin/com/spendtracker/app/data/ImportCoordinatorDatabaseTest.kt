package com.spendtracker.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.importing.HistoryCutoffProvider
import com.spendtracker.core.importing.ImportCoordinator
import com.spendtracker.core.importing.ImportMode
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.MessageSource
import com.spendtracker.core.importing.SourceFingerprinter
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.repository.RoomImportStateRepository
import com.spendtracker.core.repository.RoomTransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the shared importer against the actual Android Room database.
 * The source is synthetic and bypasses the SMS provider, isolating streaming,
 * parsing, persistence, and durable completion without requiring private data.
 */
@RunWith(AndroidJUnit4::class)
class ImportCoordinatorDatabaseTest {
    @Test
    fun unavailableProviderCursorFailsInsteadOfReportingEmptyInbox() {
        val reader = SmsInboxReader { null }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { reader.readMessagesSince(1_000) {} }
        }
    }

    @Test
    fun syntheticSourceImportsIntoRealDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "import-test-${System.nanoTime()}.db"
        val database = createSpendTrackerDatabase(context, databaseName)
        try {
            val transactionRepository = RoomTransactionRepository(
                database.transactionDao(),
                database.merchantCategoryRuleDao(),
                nowEpochMillis = { 5_000 },
            )
            val stateRepository = RoomImportStateRepository(database.appStateDao())
            val source = object : MessageSource {
                override suspend fun readMessagesSince(
                    cutoffEpochMillis: Long,
                    consume: suspend (SourceMessage) -> Unit,
                ): Int {
                    consume(message("1", "INR 250.00 debited at Northstar Cafe"))
                    consume(message("2", "Synthetic delivery notice"))
                    return 2
                }
            }
            val coordinator = ImportCoordinator(
                messageSource = source,
                parser = FinancialMessageParser(),
                fingerprinter = SourceFingerprinter { "fingerprint-${it.sourceId}" },
                transactionRepository = transactionRepository,
                importStateRepository = stateRepository,
                historyCutoffProvider = HistoryCutoffProvider { 1_000 },
                nowEpochMillis = { 5_000 },
            )

            val result = coordinator.run(ImportMode.INITIAL) {}

            assertEquals(ImportRunStatus.COMPLETED, result.status)
            assertEquals(2, result.progress.scannedMessages)
            assertEquals(1, result.progress.savedTransactions)
            assertEquals(1, database.transactionDao().count())
            assertTrue(stateRepository.getImportState().initialImportComplete)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun message(id: String, body: String) = SourceMessage(
        sourceId = id,
        sender = "SYNTHETIC",
        body = body,
        receivedAtEpochMillis = 2_000,
    )
}
