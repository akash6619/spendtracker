package com.spendtracker.app.data

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps one persistent one-time worker targeted at the next 22:00 Asia/Kolkata.
 *
 * Each completed worker appends a newly wall-clock-aligned request. This avoids
 * accumulating timing drift after Android defers an earlier execution.
 */
object DailySpendSummaryScheduler {
    private val reportingZone = ZoneId.of("Asia/Kolkata")

    fun ensureScheduled(context: Context, now: Instant = Instant.now()) {
        enqueue(context, scheduleSpec(now), ExistingWorkPolicy.KEEP)
    }

    /** Appends the next wall-clock-aligned run after the current worker. */
    fun scheduleNext(context: Context, now: Instant = Instant.now()) {
        enqueue(context, scheduleSpec(now), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    internal fun scheduleSpec(now: Instant): ScheduleSpec {
        val localNow = now.atZone(reportingZone)
        var target = localNow.toLocalDate().atTime(22, 0).atZone(reportingZone)
        if (!target.toInstant().isAfter(now)) target = target.plusDays(1)
        return ScheduleSpec(
            reportingDay = target.toLocalDate(),
            delayMillis = Duration.between(now, target.toInstant()).toMillis(),
        )
    }

    private fun enqueue(context: Context, spec: ScheduleSpec, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DailySpendSummaryWorker>()
            .setInitialDelay(spec.delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DailySpendSummaryWorker.INPUT_REPORTING_DAY, spec.reportingDay.toString())
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    /** Target date and elapsed delay captured for one scheduled summary. */
    internal data class ScheduleSpec(
        val reportingDay: LocalDate,
        val delayMillis: Long,
    )

    private const val UNIQUE_WORK_NAME = "daily-spend-summary-22-ist-v2"
}
