package com.spendtracker.app

import android.app.Application
import com.spendtracker.app.data.AndroidSourceFingerprinter
import com.spendtracker.app.data.SmsMessageScanner
import com.spendtracker.core.database.createSpendTrackerDatabase
import com.spendtracker.core.repository.RoomTransactionRepository

/**
 * Application-level composition root for production dependencies.
 *
 * It lazily owns the Room database, repository, Android Keystore fingerprinter,
 * and SMS scanner so configuration changes do not recreate them. It also performs
 * complete local deletion in the required database-then-key order.
 */
class SpendTrackerApplication : Application() {
    val database by lazy { createSpendTrackerDatabase(this) }
    val repository by lazy {
        RoomTransactionRepository(database.transactionDao(), System::currentTimeMillis)
    }
    val fingerprinter by lazy { AndroidSourceFingerprinter() }
    val messageScanner by lazy { SmsMessageScanner(this, fingerprinter) }

    /** Full local reset: facts first, then the installation-local fingerprint key. */
    suspend fun deleteAllLocalData() {
        database.clearAllTables()
        fingerprinter.deleteKey()
    }
}
