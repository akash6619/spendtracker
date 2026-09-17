package com.spendtracker.app.ui

import com.spendtracker.app.data.SourceUnavailableReason
import com.spendtracker.core.aggregation.DashboardPeriod
import com.spendtracker.core.aggregation.PeriodComparison
import com.spendtracker.core.aggregation.PeriodReport
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.TransactionFilter

/**
 * Identifies which top-level experience Compose should render.
 * Initialization waits for durable state, onboarding owns the first import, and
 * main renders completed local data or an explicitly selected debug demo.
 */
enum class AppStage {
    INITIALIZING,
    ONBOARDING,
    MAIN,
}

/**
 * Complete SMS read-permission lifecycle rendered by onboarding and settings.
 * Request history plus Android rationale state distinguishes retryable denial,
 * permanent denial, and access revoked after a successful import.
 */
enum class PermissionUiState {
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED,
    GRANTED,
    REVOKED,
}

/**
 * Screens reachable through the current main bottom navigation.
 * The selected value is held by [AppViewModel] while it remains alive.
 */
enum class TopLevelDestination {
    DASHBOARD,
    TRANSACTIONS,
    SETTINGS,
}

/**
 * Coarse, non-sensitive errors safe to expose to the UI.
 * It currently represents scan failure without carrying SMS content, identifiers,
 * amounts, or exception details into presentation state.
 */
enum class AppError {
    SCAN_FAILED,
}

/**
 * Safe aggregate feedback from the most recently completed scan.
 * It intentionally contains counts only and never source-message details.
 */
data class ScanSummaryUiState(
    val scannedMessages: Int,
    val recognizedTransactions: Int,
    val rejectedMessages: Int = 0,
    val reviewTransactions: Int = 0,
    val savedTransactions: Int = 0,
)

/**
 * Render-ready dashboard state for the selected reporting period.
 *
 * [periodOffset] is zero for the current calendar period and negative while
 * browsing history. [report] carries the shared period facts; it is null only
 * while the ledger is empty. Aggregation rules live in shared types.
 */
data class DashboardUiState(
    val period: DashboardPeriod = DashboardPeriod.WEEK,
    val periodOffset: Int = 0,
    val report: PeriodReport? = null,
    val comparison: PeriodComparison? = null,
    val hasTransactions: Boolean = false,
    val isDemo: Boolean = false,
)

/**
 * Ephemeral state for the original message shown with transaction detail.
 * [Found] holds the raw body only while that detail is open; the ViewModel clears
 * it on selection change and when the detail closes. The body is never added to
 * the transaction, persisted, cached, or logged.
 */
sealed interface SourceViewUiState {
    data object Loading : SourceViewUiState

    data class Found(
        val sender: String,
        val body: String,
        val receivedAtEpochMillis: Long,
    ) : SourceViewUiState

    data class Unavailable(val reason: SourceUnavailableReason) : SourceViewUiState
}

/**
 * Render-ready state for the current transaction list.
 * It carries filtered records plus the selected detail independently of filtering,
 * so saving an edit never unexpectedly closes a record that leaves the result set.
 * Errors are coarse flags and never contain storage exception details.
 */
data class TransactionsUiState(
    val transactions: List<LedgerTransaction> = emptyList(),
    val isDemo: Boolean = false,
    val filter: TransactionFilter = TransactionFilter(),
    val selected: LedgerTransaction? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val saveSucceeded: Boolean = false,
    val sourceView: SourceViewUiState? = null,
)

/**
 * Render-ready privacy/status facts and the destructive delete-all lifecycle.
 * Values are coarse and non-sensitive; last scan and parser version come from
 * durable import state, the stored count from the real (non-demo) ledger.
 */
data class SettingsUiState(
    val lastScanEpochMillis: Long? = null,
    val parserVersion: Int = 1,
    val storedTransactionCount: Int = 0,
    val showDeleteConfirm: Boolean = false,
    val isDeletingAll: Boolean = false,
    val deleteAllFailed: Boolean = false,
    val deleteAllSucceeded: Boolean = false,
)

/**
 * Immutable snapshot consumed by the top-level Compose application.
 * It combines permission, navigation, scan progress/error, demo selection, and
 * child-screen state so rendering remains a pure function of one value.
 */
data class AppUiState(
    val stage: AppStage = AppStage.INITIALIZING,
    val permission: PermissionUiState = PermissionUiState.NOT_REQUESTED,
    val selectedDestination: TopLevelDestination = TopLevelDestination.DASHBOARD,
    val isScanning: Boolean = false,
    val importStatus: ImportRunStatus = ImportRunStatus.NOT_STARTED,
    val importProgress: ImportProgress = ImportProgress(),
    val scanSummary: ScanSummaryUiState? = null,
    val error: AppError? = null,
    val demoAvailable: Boolean = false,
    val usingDemoData: Boolean = false,
    val dashboard: DashboardUiState = DashboardUiState(),
    val transactions: TransactionsUiState = TransactionsUiState(),
    val settings: SettingsUiState = SettingsUiState(),
)
