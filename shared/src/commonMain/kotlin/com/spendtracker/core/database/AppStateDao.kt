package com.spendtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persists singleton import and settings rows used to restore app lifecycle state.
 * Queries expose only coarse status, permission history, and aggregate counters;
 * no source-message content enters either table.
 */
@Dao
interface AppStateDao {
    @Query("SELECT * FROM import_state WHERE id = 1")
    fun observeImportState(): Flow<ImportStateEntity?>

    @Query("SELECT * FROM import_state WHERE id = 1")
    suspend fun getImportState(): ImportStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveImportState(state: ImportStateEntity)

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)
}
