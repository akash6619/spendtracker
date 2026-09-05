package com.spendtracker.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        TransactionEntity::class,
        ImportStateEntity::class,
        MerchantCategoryRuleEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(SpendTrackerDatabaseConstructor::class)
/**
 * Shared Room database for the complete on-device SpendTracker state.
 *
 * It currently owns transactions, import progress, merchant rules, and settings.
 * KSP generates platform constructors, while platform source sets provide the
 * database path and this file applies a consistent bundled SQLite driver.
 */
abstract class SpendTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
/** KSP-provided constructor used by Room to create the database on each target. */
expect object SpendTrackerDatabaseConstructor : RoomDatabaseConstructor<SpendTrackerDatabase> {
    override fun initialize(): SpendTrackerDatabase
}

fun RoomDatabase.Builder<SpendTrackerDatabase>.buildSpendTrackerDatabase(): SpendTrackerDatabase =
    // Bundled SQLite keeps behavior consistent across Android, JVM tests, and iOS.
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
