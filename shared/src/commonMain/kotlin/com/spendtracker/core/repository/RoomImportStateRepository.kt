package com.spendtracker.core.repository

import com.spendtracker.core.database.AppStateDao
import com.spendtracker.core.database.ImportStateEntity
import com.spendtracker.core.database.SettingsEntity
import com.spendtracker.core.importing.ImportFailureCode
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.ImportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of durable import and permission-history state.
 * It translates storage strings into domain enums and supplies safe defaults
 * before singleton rows have been created on a fresh installation.
 */
class RoomImportStateRepository(
    private val dao: AppStateDao,
) : ImportStateRepository {
    override fun observeImportState(): Flow<ImportState> =
        dao.observeImportState().map { it?.toDomain() ?: ImportState() }

    override suspend fun getImportState(): ImportState =
        dao.getImportState()?.toDomain() ?: ImportState()

    override suspend fun saveImportState(state: ImportState) {
        dao.saveImportState(state.toEntity())
    }

    override suspend fun wasSmsPermissionRequested(): Boolean =
        dao.getSettings()?.smsPermissionRequested ?: false

    override suspend fun setSmsPermissionRequested(requested: Boolean) {
        val current = dao.getSettings() ?: SettingsEntity()
        dao.saveSettings(current.copy(smsPermissionRequested = requested))
    }
}

private fun ImportState.toEntity(): ImportStateEntity = ImportStateEntity(
    status = status.name,
    initialImportComplete = initialImportComplete,
    lastScanEpochMillis = lastSuccessfulScanEpochMillis,
    lastAttemptEpochMillis = lastAttemptEpochMillis,
    lastScannedCount = progress.scannedMessages,
    lastRecognizedCount = progress.recognizedTransactions,
    lastRejectedCount = progress.rejectedMessages,
    lastReviewCount = progress.reviewTransactions,
    lastSavedCount = progress.savedTransactions,
    parserVersion = parserVersion,
    failureCode = failureCode?.name,
)

private fun ImportStateEntity.toDomain(): ImportState = ImportState(
    status = ImportRunStatus.valueOf(status),
    initialImportComplete = initialImportComplete,
    lastSuccessfulScanEpochMillis = lastScanEpochMillis,
    lastAttemptEpochMillis = lastAttemptEpochMillis,
    progress = ImportProgress(
        scannedMessages = lastScannedCount,
        recognizedTransactions = lastRecognizedCount,
        rejectedMessages = lastRejectedCount,
        reviewTransactions = lastReviewCount,
        savedTransactions = lastSavedCount,
    ),
    parserVersion = parserVersion,
    failureCode = failureCode?.let(ImportFailureCode::valueOf),
)
