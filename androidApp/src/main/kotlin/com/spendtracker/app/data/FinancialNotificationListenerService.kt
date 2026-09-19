package com.spendtracker.app.data

import android.Manifest
import android.app.Notification
import android.content.pm.PackageManager
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.spendtracker.app.SpendTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Uses notifications from the current default SMS application only as signals
 * to inspect recent SMS.
 *
 * Notification titles and bodies are never read, persisted, or logged. A short
 * delayed provider scan runs off the callback thread, and repository deduplication
 * ensures notification updates and later foreground reconciliation remain safe.
 */
class FinancialNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ingestMutex = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return
        if (sbn.packageName != defaultSmsPackage ||
            sbn.notification.category != Notification.CATEGORY_MESSAGE
        ) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return

        scope.launch {
            try {
                delay(PROVIDER_SETTLE_DELAY_MILLIS)
                ingestMutex.withLock {
                    val dependencies = application as SpendTrackerApplication
                    dependencies.liveMessageIngestor
                        .ingestSince((sbn.postTime - RECENT_WINDOW_MILLIS).coerceAtLeast(0L))
                        .forEach(dependencies.liveTransactionNotifier::notify)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Foreground reconciliation is the recovery path. Never log source details.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val PROVIDER_SETTLE_DELAY_MILLIS = 750L
        const val RECENT_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
