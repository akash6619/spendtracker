package com.spendtracker.core.repository

import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.importing.ImportFailureCode
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.ImportState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the Room adapter preserves every safe lifecycle field on disk.
 * Closing and reopening the database catches mappings that would appear correct
 * in memory but fail to survive process death.
 */
class RoomImportStateRepositoryTest {
    @Test
    fun importAndPermissionStateSurviveDatabaseReopen() = runTest {
        val file = Files.createTempFile("spendtracker-state", ".db").toFile().apply { delete() }
        val expected = ImportState(
            status = ImportRunStatus.FAILED,
            initialImportComplete = true,
            lastSuccessfulScanEpochMillis = 8_000,
            lastAttemptEpochMillis = 9_000,
            progress = ImportProgress(12, 8, 4, 2, 7),
            failureCode = ImportFailureCode.INTERRUPTED,
        )

        val first = createSpendTrackerDatabase(file)
        val firstRepository = RoomImportStateRepository(first.appStateDao())
        assertFalse(firstRepository.wasSmsPermissionRequested())
        firstRepository.saveImportState(expected)
        firstRepository.setSmsPermissionRequested(true)
        first.close()

        val reopened = createSpendTrackerDatabase(file)
        val reopenedRepository = RoomImportStateRepository(reopened.appStateDao())
        assertEquals(expected, reopenedRepository.observeImportState().first())
        assertTrue(reopenedRepository.wasSmsPermissionRequested())
        reopened.close()
        file.delete()
    }
}
