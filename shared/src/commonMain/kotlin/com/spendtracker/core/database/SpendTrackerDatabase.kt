package com.spendtracker.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        TransactionEntity::class,
        ImportStateEntity::class,
        MerchantCategoryRuleEntity::class,
        SettingsEntity::class,
    ],
    version = 3,
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
    abstract fun appStateDao(): AppStateDao
    abstract fun merchantCategoryRuleDao(): MerchantCategoryRuleDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
/** KSP-provided constructor used by Room to create the database on each target. */
expect object SpendTrackerDatabaseConstructor : RoomDatabaseConstructor<SpendTrackerDatabase> {
    override fun initialize(): SpendTrackerDatabase
}

fun RoomDatabase.Builder<SpendTrackerDatabase>.buildSpendTrackerDatabase(): SpendTrackerDatabase =
    // Bundled SQLite keeps behavior consistent across Android, JVM tests, and iOS.
    setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .addMigrations(MIGRATION_2_3)
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

/** Adds durable import and permission lifecycle fields without changing transaction data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execute("ALTER TABLE import_state ADD COLUMN status TEXT NOT NULL DEFAULT 'NOT_STARTED'")
        connection.execute("ALTER TABLE import_state ADD COLUMN lastAttemptEpochMillis INTEGER DEFAULT NULL")
        connection.execute("ALTER TABLE import_state ADD COLUMN lastRejectedCount INTEGER NOT NULL DEFAULT 0")
        connection.execute("ALTER TABLE import_state ADD COLUMN lastReviewCount INTEGER NOT NULL DEFAULT 0")
        connection.execute("ALTER TABLE import_state ADD COLUMN lastSavedCount INTEGER NOT NULL DEFAULT 0")
        connection.execute("ALTER TABLE import_state ADD COLUMN failureCode TEXT DEFAULT NULL")
        connection.execute("ALTER TABLE settings ADD COLUMN smsPermissionRequested INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds privacy-safe durable review reasons so category-only uncertainty can be resolved. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execute("ALTER TABLE transactions ADD COLUMN reviewReasons TEXT NOT NULL DEFAULT ''")
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement -> statement.step() }
}
