package com.spendtracker.app.ui

import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.model.LedgerTransaction

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
 * Render-ready summary for the current dashboard shell.
 * It includes only effectively included INR spend plus counts for included and
 * foreign transactions; period analytics arrive in MVP-07.
 */
data class DashboardUiState(
    val inrSpendMinor: Long = 0,
    val includedTransactions: Int = 0,
    val foreignTransactions: Int = 0,
    val isDemo: Boolean = false,
)

/**
 * Render-ready state for the current transaction list.
 * It carries the repository's ordered ledger records and whether they came from
 * debug demo mode; filters and editing arrive in MVP-06.
 */
data class TransactionsUiState(
    val transactions: List<LedgerTransaction> = emptyList(),
    val isDemo: Boolean = false,
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
)
