package com.spendtracker.app.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendtracker.app.MainActivity
import com.spendtracker.app.R
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.core.model.LedgerTransaction

/**
 * Publishes a private, compact alert for a transaction already stored locally.
 *
 * Only parsed facts enter the notification. Its immutable [PendingIntent] carries
 * the app transaction ID so a tap can open the matching detail screen.
 */
class LiveTransactionNotifier(private val context: Context) {
    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.transaction_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.transaction_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    fun notify(transaction: LedgerTransaction) {
        if (!canPostAlerts()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val merchant = transaction.merchant ?: context.getString(R.string.transaction_unknown_merchant)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRANSACTION
            putExtra(EXTRA_TRANSACTION_ID, transaction.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            transaction.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val detail = context.getString(
            R.string.transaction_notification_detail,
            categoryLabel(transaction),
            if (transaction.includedInSpend) {
                context.getString(R.string.transaction_notification_included)
            } else {
                context.getString(R.string.transaction_notification_excluded)
            },
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transaction)
            .setContentTitle(context.getString(
                R.string.transaction_notification_title,
                formatMoney(transaction.money),
                merchant,
            ))
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(transaction.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the checks and this platform call.
        }
    }

    /** Reports whether Android currently permits this app and channel to alert. */
    fun canPostAlerts(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun categoryLabel(transaction: LedgerTransaction): String = context.getString(
        when (transaction.category) {
            com.spendtracker.core.model.SpendCategory.FOOD_AND_DINING -> R.string.category_food_and_dining
            com.spendtracker.core.model.SpendCategory.GROCERIES -> R.string.category_groceries
            com.spendtracker.core.model.SpendCategory.TRANSPORT -> R.string.category_transport
            com.spendtracker.core.model.SpendCategory.SHOPPING -> R.string.category_shopping
            com.spendtracker.core.model.SpendCategory.BILLS_AND_UTILITIES -> R.string.category_bills_and_utilities
            com.spendtracker.core.model.SpendCategory.HOUSING -> R.string.category_housing
            com.spendtracker.core.model.SpendCategory.HEALTH -> R.string.category_health
            com.spendtracker.core.model.SpendCategory.ENTERTAINMENT -> R.string.category_entertainment
            com.spendtracker.core.model.SpendCategory.TRAVEL -> R.string.category_travel
            com.spendtracker.core.model.SpendCategory.EDUCATION -> R.string.category_education
            com.spendtracker.core.model.SpendCategory.SUBSCRIPTIONS -> R.string.category_subscriptions
            com.spendtracker.core.model.SpendCategory.FEES_AND_CHARGES -> R.string.category_fees_and_charges
            com.spendtracker.core.model.SpendCategory.OTHER -> R.string.category_other
        },
    )

    companion object {
        const val EXTRA_TRANSACTION_ID = "com.spendtracker.app.extra.TRANSACTION_ID"
        const val ACTION_OPEN_TRANSACTION = "com.spendtracker.app.action.OPEN_TRANSACTION"
        // A new ID upgrades existing installs whose original default-importance
        // channel cannot be raised programmatically after Android creates it.
        private const val CHANNEL_ID = "recognized_transaction_alerts"
    }
}
