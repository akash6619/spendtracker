package com.spendtracker.app

import android.app.Application
import com.spendtracker.app.data.AndroidSourceFingerprinter
import com.spendtracker.app.data.CalendarHistoryCutoffProvider
import com.spendtracker.app.data.SmsInboxReader
import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.importing.ImportCoordinator
import com.spendtracker.core.parser.FinancialMessageParser
import com.spendtracker.core.repository.RoomImportStateRepository
import com.spendtracker.core.repository.RoomTransactionRepository

/**
 * Application-level composition root for production dependencies.
 *
 * It lazily owns the Room database, repositories, Android Keystore fingerprinter,
 * and import coordinator so configuration changes do not recreate them. It also
 * performs complete local deletion in the required database-then-key order.
 */
class SpendTrackerApplication : Application() {
    val database by lazy { createSpendTrackerDatabase(this) }
    val repository by lazy {
        RoomTransactionRepository(database.transactionDao(), System::currentTimeMillis)
    }
    val importStateRepository by lazy { RoomImportStateRepository(database.appStateDao()) }
    val fingerprinter by lazy { AndroidSourceFingerprinter() }
    val importCoordinator by lazy {
        ImportCoordinator(
            messageSource = SmsInboxReader(this),
            parser = FinancialMessageParser(),
            fingerprinter = fingerprinter,
            transactionRepository = repository,
            importStateRepository = importStateRepository,
            historyCutoffProvider = CalendarHistoryCutoffProvider(),
            nowEpochMillis = System::currentTimeMillis,
        )
    }

    /** Full local reset: facts first, then the installation-local fingerprint key. */
    suspend fun deleteAllLocalData() {
        database.clearAllTables()
        fingerprinter.deleteKey()
    }
}
