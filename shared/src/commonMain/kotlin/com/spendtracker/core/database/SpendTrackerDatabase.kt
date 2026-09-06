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
    version = 5,
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
        .addMigrations(MIGRATION_3_4)
        .addMigrations(MIGRATION_4_5)
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

/**
 * Collapses the detected/user override split into one value per field.
 * The category keeps its detected value, spend inclusion is folded from the
 * effective override, rows with any override are marked user-edited so future
 * imports cannot refresh them, and the override columns disappear.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execute("ALTER TABLE transactions RENAME COLUMN detectedCategory TO category")
        connection.execute("ALTER TABLE transactions ADD COLUMN includedInSpend INTEGER NOT NULL DEFAULT 0")
        connection.execute("ALTER TABLE transactions ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
        connection.execute(
            "UPDATE transactions SET includedInSpend = COALESCE(userIncludedInSpend, detectedIncludedInSpend)",
        )
        // The pre-single-value effective category was the override when present.
        connection.execute("UPDATE transactions SET category = COALESCE(userCategory, category)")
        // Preserve re-import protection for edits made before the single-value model.
        connection.execute(
            "UPDATE transactions SET userEdited = 1 WHERE userCategory IS NOT NULL OR userIncludedInSpend IS NOT NULL",
        )
        connection.execute("ALTER TABLE transactions DROP COLUMN userIncludedInSpend")
        connection.execute("ALTER TABLE transactions DROP COLUMN detectedIncludedInSpend")
        connection.execute("ALTER TABLE transactions DROP COLUMN userCategory")
    }
}

/**
 * Adds the edit flag for databases created directly at version 4. Databases
 * migrated from version 3 already received the column (with override-derived
 * values) in MIGRATION_3_4, so the add is guarded.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val hasUserEdited = connection.prepare("PRAGMA table_info(transactions)").use { statement ->
            var found = false
            while (statement.step()) {
                if (statement.getText(1) == "userEdited") found = true
            }
            found
        }
        if (!hasUserEdited) {
            connection.execute("ALTER TABLE transactions ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
        }
    }
}

private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement -> statement.step() }
}
