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
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.core.database.TransactionDao
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SpendCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Publishes daily included-INR spend with expandable category distribution.
 *
 * Totals come only from persisted parsed facts. Raw SMS, sender, account, and
 * merchant data never enter this notification.
 */
class DailySpendSummaryNotifier(
    private val context: Context,
    private val transactionDao: TransactionDao,
) {
    private val reportingZone = ZoneId.of("Asia/Kolkata")

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.daily_summary_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.daily_summary_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    suspend fun notifyForDayContaining(now: Instant) {
        notifyForDay(now.atZone(reportingZone).toLocalDate())
    }

    /** Publishes the summary for one fixed IST calendar date. */
    suspend fun notifyForDay(day: LocalDate) {
        if (!canPost()) return
        val from = day.atStartOfDay(reportingZone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(reportingZone).toInstant().toEpochMilli()
        val snapshot = transactionDao.spendSummary(from, to)
        val total = snapshot.total
        val categories = snapshot.categories
        val formattedTotal = formatMoney(Money(total.totalMinor, CurrencyCode.INR))
        val isToday = day == LocalDate.now(reportingZone)
        val formattedDay = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val summary = if (total.transactionCount == 0) {
            if (isToday) context.getString(R.string.daily_summary_no_spend)
            else context.getString(R.string.daily_summary_no_spend_for_date, formattedDay)
        } else {
            context.resources.getQuantityString(
                R.plurals.daily_summary_count,
                total.transactionCount,
                total.transactionCount,
            )
        }
        val distribution = categories.joinToString("\n") { category ->
            val percent = if (total.totalMinor == 0L) 0 else
                (category.totalMinor.toDouble() * 100.0 / total.totalMinor).roundToInt()
            context.getString(
                R.string.daily_summary_category,
                context.getString(SpendCategory.valueOf(category.category).labelResource()),
                formatMoney(Money(category.totalMinor, CurrencyCode.INR)),
                percent,
            )
        }.ifEmpty { summary }
        val contentIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transaction)
            .setContentTitle(
                if (isToday) context.getString(R.string.daily_summary_title, formattedTotal)
                else context.getString(
                    R.string.daily_summary_title_for_date,
                    formattedDay,
                    formattedTotal,
                ),
            )
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(distribution))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and platform call.
        }
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private companion object {
        const val CHANNEL_ID = "daily_spend_summary"
        const val SUMMARY_NOTIFICATION_ID = 22_00
    }
}
