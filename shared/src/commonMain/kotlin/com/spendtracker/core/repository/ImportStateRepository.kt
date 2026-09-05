package com.spendtracker.core.repository

import com.spendtracker.core.importing.ImportState
import kotlinx.coroutines.flow.Flow

/**
 * Persists import progress and the minimum permission history needed by the UI.
 * The state contains only aggregate counters and coarse status; source-message
 * content and identifiers are outside this contract.
 */
interface ImportStateRepository {
    fun observeImportState(): Flow<ImportState>

    suspend fun getImportState(): ImportState

    suspend fun saveImportState(state: ImportState)

    suspend fun wasSmsPermissionRequested(): Boolean

    suspend fun setSmsPermissionRequested(requested: Boolean)
}
