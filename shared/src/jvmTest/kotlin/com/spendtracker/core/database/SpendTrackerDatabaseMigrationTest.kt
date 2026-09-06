package com.spendtracker.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.spendtracker.core.repository.RoomImportStateRepository
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opens a literal version-1 database through the current version-5 builder.
 * This validates the full migration chain (override columns removed, category
 * renamed, inclusion folded, userEdited added) while confirming old data
 * remains intact.
 */
class SpendTrackerDatabaseMigrationTest {
    @Test
    fun migrationFromOnePreservesStateAndAddsLifecycleDefaults() = runTest {
        val file = Files.createTempFile("spendtracker-v1", ".db").toFile().apply { delete() }
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            V1_SCHEMA.forEach(connection::execute)
            connection.execute(
                "INSERT INTO import_state VALUES (1, 1, 7000, 12, 8, 1)",
            )
            connection.execute("INSERT INTO settings VALUES (1, 1)")
            connection.execute(
                """INSERT INTO transactions VALUES (
                    'row-1', 'ANDROID_SMS', 'provider-1', 'fingerprint-1', 6000,
                    12500, 'INR', 'DEBIT', 'PURCHASE', 'OTHER', NULL,
                    'SYNTHETIC STORE', NULL, 0.6, 1, 1, NULL, 6000, 6000
                )""".trimIndent(),
            )
            connection.execute(
                """INSERT INTO transactions VALUES (
                    'row-2', 'ANDROID_SMS', 'provider-2', 'fingerprint-2', 7000,
                    5000, 'INR', 'DEBIT', 'PURCHASE', 'FOOD_AND_DINING', 'TRAVEL',
                    'SYNTHETIC STORE', NULL, 0.6, 1, 1, 0, 7000, 7000
                )""".trimIndent(),
            )
            connection.execute("PRAGMA user_version = 1")
        }

        val migrated = createSpendTrackerDatabase(file)
        val repository = RoomImportStateRepository(migrated.appStateDao())
        val state = repository.getImportState()

        assertTrue(state.initialImportComplete)
        assertEquals(7_000, state.lastSuccessfulScanEpochMillis)
        assertEquals(12, state.progress.scannedMessages)
        assertEquals(8, state.progress.recognizedTransactions)
        assertEquals(0, state.progress.rejectedMessages)
        assertFalse(repository.wasSmsPermissionRequested())
        val migratedRow = migrated.transactionDao().findById("row-1")!!
        assertTrue(migratedRow.reviewReasons.isEmpty())
        // Version-4 collapse: category keeps its detected value, inclusion folds
        // from the effective override, and the override columns are gone.
        assertEquals("OTHER", migratedRow.category)
        assertTrue(migratedRow.includedInSpend)
        assertFalse(migratedRow.userEdited)

        // A row with pre-single-value overrides keeps its inclusion and is
        // marked user-edited so re-imports cannot refresh it.
        val editedRow = migrated.transactionDao().findById("row-2")!!
        assertEquals("TRAVEL", editedRow.category)
        assertFalse(editedRow.includedInSpend)
        assertTrue(editedRow.userEdited)
        migrated.close()
        file.delete()
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { it.step() }
}

private val V1_SCHEMA = listOf(
    """CREATE TABLE transactions (
        id TEXT NOT NULL PRIMARY KEY,
        sourceType TEXT NOT NULL,
        sourceProviderId TEXT,
        sourceFingerprint TEXT NOT NULL,
        sourceReceivedAtEpochMillis INTEGER NOT NULL,
        amountMinor INTEGER NOT NULL,
        currency TEXT NOT NULL,
        direction TEXT NOT NULL,
        kind TEXT NOT NULL,
        detectedCategory TEXT NOT NULL,
        userCategory TEXT,
        merchant TEXT,
        accountHint TEXT,
        confidence REAL NOT NULL,
        parserVersion INTEGER NOT NULL,
        detectedIncludedInSpend INTEGER NOT NULL,
        userIncludedInSpend INTEGER,
        createdAtEpochMillis INTEGER NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL
    )""".trimIndent(),
    "CREATE UNIQUE INDEX index_transactions_sourceType_sourceProviderId ON transactions (sourceType, sourceProviderId)",
    "CREATE UNIQUE INDEX index_transactions_sourceType_sourceFingerprint ON transactions (sourceType, sourceFingerprint)",
    "CREATE INDEX index_transactions_sourceReceivedAtEpochMillis ON transactions (sourceReceivedAtEpochMillis)",
    "CREATE INDEX index_transactions_detectedCategory ON transactions (detectedCategory)",
    """CREATE TABLE import_state (
        id INTEGER NOT NULL PRIMARY KEY,
        initialImportComplete INTEGER NOT NULL,
        lastScanEpochMillis INTEGER,
        lastScannedCount INTEGER NOT NULL,
        lastRecognizedCount INTEGER NOT NULL,
        parserVersion INTEGER NOT NULL
    )""".trimIndent(),
    """CREATE TABLE merchant_category_rules (
        normalizedMerchant TEXT NOT NULL PRIMARY KEY,
        category TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL
    )""".trimIndent(),
    "CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, onboardingSeen INTEGER NOT NULL)",
    "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
    "INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'edc62df1a0b3cb02b0b214a0baf056cf')",
)
