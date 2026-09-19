package com.spendtracker.app

import android.app.Application
import com.spendtracker.app.data.AndroidSourceFingerprinter
import com.spendtracker.app.data.CalendarHistoryCutoffProvider
import com.spendtracker.app.data.SmsInboxReader
import com.spendtracker.app.data.SmsSourceLookup
import com.spendtracker.app.data.LiveTransactionNotifier
import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.importing.ImportCoordinator
import com.spendtracker.core.importing.ImportMode
import com.spendtracker.core.importing.LiveMessageIngestor
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
        RoomTransactionRepository(
            database.transactionDao(),
            database.merchantCategoryRuleDao(),
            System::currentTimeMillis,
        )
    }
    val importStateRepository by lazy { RoomImportStateRepository(database.appStateDao()) }
    val fingerprinter by lazy { AndroidSourceFingerprinter() }
    val sourceLookup by lazy { SmsSourceLookup(this, fingerprinter) }
    val liveTransactionNotifier by lazy { LiveTransactionNotifier(this) }
    val liveMessageIngestor by lazy {
        LiveMessageIngestor(
            messageSource = SmsInboxReader(this),
            parser = FinancialMessageParser(),
            fingerprinter = fingerprinter,
            transactionRepository = repository,
        )
    }
    val importCoordinator by lazy {
        ImportCoordinator(
            messageSource = SmsInboxReader(this),
            parser = FinancialMessageParser(),
            fingerprinter = fingerprinter,
            transactionRepository = repository,
            importStateRepository = importStateRepository,
            historyCutoffProvider = CalendarHistoryCutoffProvider(),
            nowEpochMillis = System::currentTimeMillis,
            onTransactionsInserted = { mode, transactions ->
                if (mode == ImportMode.RECONCILIATION) {
                    val cutoff = System.currentTimeMillis() - RECENT_ALERT_WINDOW_MILLIS
                    transactions.filter { it.sourceReceivedAtEpochMillis >= cutoff }
                        .forEach { transaction ->
                            runCatching { liveTransactionNotifier.notify(transaction) }
                        }
                }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        liveTransactionNotifier.createChannel()
    }

    /** Full local reset: facts first, then the installation-local fingerprint key. */
    suspend fun deleteAllLocalData() {
        database.clearAllTables()
        fingerprinter.deleteKey()
    }

    private companion object {
        const val RECENT_ALERT_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
