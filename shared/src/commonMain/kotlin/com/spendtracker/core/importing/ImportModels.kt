package com.spendtracker.core.importing

import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.parser.RejectionReason

/** Describes the durable lifecycle of the most recent import attempt. */
enum class ImportRunStatus {
    NOT_STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
}

/** Coarse failure reason safe to persist and show without message details. */
enum class ImportFailureCode {
    INTERRUPTED,
    SOURCE_OR_STORAGE_FAILURE,
}

/** Distinguishes the initial history load from a smaller overlap reconciliation. */
enum class ImportMode {
    INITIAL,
    RECONCILIATION,
}

/**
 * Safe counters emitted while an import is running.
 * It contains no sender, body, amount, timestamp, or source identifier. Reason
 * counts are attempt-local aggregate diagnostics and are not stored in Room v3.
 */
data class ImportProgress(
    val scannedMessages: Int = 0,
    val recognizedTransactions: Int = 0,
    val rejectedMessages: Int = 0,
    val reviewTransactions: Int = 0,
    val savedTransactions: Int = 0,
    val rejectionReasonCounts: Map<RejectionReason, Int> = emptyMap(),
)

/**
 * Durable import state used to restore onboarding and retry behavior.
 * A successful reconciliation keeps `initialImportComplete` true, while a failed
 * attempt retains safe counters and a coarse failure code for recovery UI.
 */
data class ImportState(
    val status: ImportRunStatus = ImportRunStatus.NOT_STARTED,
    val initialImportComplete: Boolean = false,
    val lastSuccessfulScanEpochMillis: Long? = null,
    val lastAttemptEpochMillis: Long? = null,
    val progress: ImportProgress = ImportProgress(),
    val parserVersion: Int = 1,
    val failureCode: ImportFailureCode? = null,
)

/**
 * Supplies an installation-local, non-reversible identity for a source message.
 * Platform implementations may inspect the ephemeral body but return only the
 * fingerprint used by repository deduplication.
 */
fun interface SourceFingerprinter {
    fun fingerprint(message: SourceMessage): String
}

/** Provides the exact lower timestamp bound for the initial history import. */
fun interface HistoryCutoffProvider {
    fun cutoffEpochMillis(): Long
}

/**
 * App-facing boundary for running initial or reconciliation imports.
 * The interface allows ViewModel tests to supply deterministic outcomes without
 * Android SMS or Room dependencies.
 */
fun interface ImportRunner {
    suspend fun run(
        mode: ImportMode,
        onProgress: (ImportProgress) -> Unit,
    ): ImportState
}
