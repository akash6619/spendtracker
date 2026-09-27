package com.spendtracker.app.data

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendtracker.app.SpendTrackerApplication
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the daily summary can be posted through Android's notification service. */
@RunWith(AndroidJUnit4::class)
class DailySpendSummaryNotifierTest {
    @Test
    fun postsDailySummaryNotification() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val app = context.applicationContext as SpendTrackerApplication

        app.dailySpendSummaryNotifier.notifyForDayContaining(Instant.now())

        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any { it.id == 2_200 })
        manager.cancel(2_200)
    }

    @Test
    fun labelsDelayedSummaryWithItsReportingDate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val app = context.applicationContext as SpendTrackerApplication
        val manager = context.getSystemService(NotificationManager::class.java)
        val reportingDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1)

        app.dailySpendSummaryNotifier.notifyForDay(reportingDay)

        val notification = manager.activeNotifications.single { it.id == 2_200 }.notification
        val formattedDay = reportingDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        assertTrue(notification.extras.getCharSequence("android.title").toString().contains(formattedDay))
        manager.cancel(2_200)
    }

    @Test
    fun dailyWorkflowDoesNotRepopulateAfterLocalReset() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_SMS,
        )
        val app = context.applicationContext as SpendTrackerApplication
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(2_200)
        app.deleteAllLocalData()

        app.publishDailySummary(Instant.now().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate())

        assertTrue(app.repository.count() == 0)
        assertTrue(manager.activeNotifications.none { it.id == 2_200 })
    }
}
