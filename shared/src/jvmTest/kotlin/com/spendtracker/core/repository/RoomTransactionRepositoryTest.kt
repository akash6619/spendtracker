package com.spendtracker.core.repository

import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.parser.FinancialMessageParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTransactionRepositoryTest {
    @Test
    fun persistsAcrossReopenAndDoesNotDuplicate() = runTest {
        val file = Files.createTempFile("spendtracker", ".db").toFile().apply { delete() }
        val firstDatabase = createSpendTrackerDatabase(file)
        val firstRepository = RoomTransactionRepository(firstDatabase.transactionDao()) { 100 }
        firstRepository.upsert(listOf(candidate("7", "same")))
        firstRepository.upsert(listOf(candidate("7", "same")))
        assertEquals(1, firstRepository.observeTransactions().first().size)
        firstDatabase.close()

        val reopened = createSpendTrackerDatabase(file)
        assertEquals(1, reopened.transactionDao().count())
        reopened.close()
        file.delete()
    }

    @Test
    fun providerIdReuseKeepsBothFactsAndMovesProviderId() = runTest {
        withRepository { repository, database ->
            repository.upsert(listOf(candidate("7", "old")))
            repository.upsert(listOf(candidate("7", "new", amountMinor = 999)))

            val rows = repository.observeTransactions().first()
            assertEquals(2, rows.size)
            assertNull(rows.first { it.sourceFingerprint == "old" }.sourceProviderId)
            assertEquals("7", rows.first { it.sourceFingerprint == "new" }.sourceProviderId)
            assertEquals(2, database.transactionDao().count())
        }
    }

    @Test
    fun fingerprintWinsAndUserOverridesSurviveReimport() = runTest {
        withRepository { repository, _ ->
            repository.upsert(listOf(candidate("1", "stable")))
            val original = repository.observeTransactions().first().single()
            repository.updateOverrides(original.id, SpendCategory.TRAVEL, false)
            repository.upsert(listOf(candidate("2", "stable", amountMinor = 999)))

            val updated = repository.observeTransactions().first().single()
            assertEquals("2", updated.sourceProviderId)
            assertEquals(999, updated.transaction.money.amountMinor)
            assertEquals(SpendCategory.TRAVEL, updated.effectiveCategory)
            assertFalse(updated.isIncludedInSpend)
        }
    }

    @Test
    fun parserReparseUpdatesDetectedFieldsAndPreservesExplicitOverrides() = runTest {
        withRepository { repository, _ ->
            val parser = FinancialMessageParser()
            val original = parser.parse(source("INR 5.00 paid at NORTHSTAR CAFE"))!!
            repository.upsert(listOf(candidate("1", "reparse", parsed = original)))
            val stored = repository.observeTransactions().first().single()
            repository.updateOverrides(stored.id, SpendCategory.TRAVEL, false)

            val reparsed = parser.parse(source("INR 9.99 paid at NORTHSTAR CAFE"))!!
            repository.upsert(listOf(candidate("1", "reparse", parsed = reparsed)))

            val updated = repository.observeTransactions().first().single()
            assertEquals(999, updated.transaction.money.amountMinor)
            assertEquals(2, updated.transaction.parserVersion)
            assertEquals(SpendCategory.TRAVEL, updated.effectiveCategory)
            assertFalse(updated.isIncludedInSpend)
        }
    }

    @Test
    fun conflictingReviewRecordRemainsExcludedAfterRoomRoundTrip() = runTest {
        withRepository { repository, _ ->
            val parsed = FinancialMessageParser().parse(
                source("INR 500 debited and INR 400 credited at NORTHSTAR"),
            )!!

            repository.upsert(listOf(candidate("1", "conflict", parsed = parsed)))

            assertFalse(repository.observeTransactions().first().single().isIncludedInSpend)
        }
    }

    @Test
    fun concurrentUpsertsRemainIdempotent() = runTest {
        withRepository { repository, database ->
            List(20) { async { repository.upsert(listOf(candidate("4", "one"))) } }.awaitAll()
            assertEquals(1, database.transactionDao().count())
        }
    }

    @Test
    fun periodCategoryAndReviewQueriesUseEffectiveValues() = runTest {
        withRepository { repository, database ->
            repository.upsert(listOf(
                candidate("1", "food", timestamp = 1_000, amountMinor = 250),
                candidate("2", "usd", timestamp = 1_100, currency = CurrencyCode.USD),
                candidate("3", "low", timestamp = 3_000, confidence = .2),
            ))
            val food = repository.observeTransactions().first().first { it.sourceFingerprint == "food" }
            repository.updateOverrides(food.id, SpendCategory.TRAVEL, true)

            assertEquals(2, database.transactionDao().inPeriod(900, 2_000).size)
            assertEquals(250, database.transactionDao().periodTotal(900, 2_000).totalMinor)
            val totals = database.transactionDao().categoryTotals(900, 2_000)
            assertEquals(SpendCategory.TRAVEL.name, totals.single().category)
            assertEquals(250, totals.single().totalMinor)
            assertEquals("low", database.transactionDao().needingReview(.5).single().sourceFingerprint)
            assertTrue(repository.getById(food.id) != null)
            repository.clear()
            assertEquals(0, database.transactionDao().count())
        }
    }

    private suspend fun withRepository(
        block: suspend (RoomTransactionRepository, com.spendtracker.core.database.SpendTrackerDatabase) -> Unit,
    ) {
        val file = Files.createTempFile("spendtracker", ".db").toFile().apply { delete() }
        val database = createSpendTrackerDatabase(file)
        try {
            block(RoomTransactionRepository(database.transactionDao()) { 100 }, database)
        } finally {
            database.close()
            file.delete()
        }
    }

    private fun candidate(
        providerId: String,
        fingerprint: String,
        timestamp: Long = 1_000,
        amountMinor: Long = 500,
        currency: CurrencyCode = CurrencyCode.INR,
        confidence: Double = .9,
        parsed: ParsedTransaction? = null,
    ) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = providerId,
        sourceFingerprint = fingerprint,
        transaction = parsed ?: ParsedTransaction(
            sourceId = providerId,
            sourceReceivedAtEpochMillis = timestamp,
            money = Money(amountMinor, currency),
            direction = TransactionDirection.DEBIT,
            kind = TransactionKind.PURCHASE,
            category = SpendCategory.FOOD_AND_DINING,
            merchant = "Merchant",
            accountHint = "1234",
            confidence = confidence,
            parserVersion = 1,
        ),
    )

    private fun source(body: String) = SourceMessage("1", "SYNTHETIC", body, 1_000)
}
