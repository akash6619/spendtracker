package com.spendtracker.app.data

import com.spendtracker.core.importing.LiveMessageIngestor
import com.spendtracker.core.importing.MessageSource
import com.spendtracker.core.importing.SourceFingerprinter
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.parser.FinancialMessageParser
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies notification-triggered ingestion saves and reports each source once. */
class LiveMessageIngestorTest {
    @Test
    fun acceptedRecentMessageIsReturnedOnceAndRejectedTextIsIgnored() = runTest {
        val source = object : MessageSource {
            override suspend fun readMessagesSince(
                cutoffEpochMillis: Long,
                consume: suspend (SourceMessage) -> Unit,
            ): Int {
                listOf(
                    SourceMessage("sms-1", "VM-BANK", "INR 125.00 debited at Cedar Cafe", cutoffEpochMillis + 1),
                    SourceMessage("sms-2", "SHOP", "Weekend sale starts now", cutoffEpochMillis + 2),
                ).forEach { consume(it) }
                return 2
            }
        }
        val repository = InMemoryTransactionRepository()
        val ingestor = LiveMessageIngestor(
            messageSource = source,
            parser = FinancialMessageParser(),
            fingerprinter = SourceFingerprinter { "fingerprint-${it.sourceId}" },
            transactionRepository = repository,
        )

        assertEquals(1, ingestor.ingestSince(1_000).size)
        assertEquals(emptyList(), ingestor.ingestSince(1_000))
        assertEquals(1, repository.count())
    }
}
