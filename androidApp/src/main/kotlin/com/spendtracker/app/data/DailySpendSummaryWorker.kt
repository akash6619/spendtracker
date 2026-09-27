package com.spendtracker.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendtracker.app.SpendTrackerApplication
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/**
 * Reconciles and reports one scheduled IST calendar day from local data.
 *
 * SMS bodies flow ephemerally through the existing on-device ingestor before
 * aggregation. Successful work schedules the next wall-clock-aligned run.
 */
class DailySpendSummaryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val reportingDay = inputData.getString(INPUT_REPORTING_DAY)
            ?.let(LocalDate::parse)
            ?: return Result.failure()
        val app = applicationContext as SpendTrackerApplication
        return try {
            app.publishDailySummary(reportingDay)
            DailySpendSummaryScheduler.scheduleNext(app)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val INPUT_REPORTING_DAY = "reporting_day"
    }
}
