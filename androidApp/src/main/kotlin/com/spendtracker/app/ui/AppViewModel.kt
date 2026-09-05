package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendtracker.core.importing.ImportMode
import com.spendtracker.core.importing.ImportFailureCode
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.ImportRunner
import com.spendtracker.core.importing.ImportState
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.repository.ImportStateRepository
import com.spendtracker.core.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the top-level application state and user-intent orchestration.
 *
 * It tracks permission and navigation, runs scans, writes accepted candidates to
 * the production repository, and derives dashboard/list state from the active
 * observable repository. Debug demo mode temporarily swaps that active source.
 */
class AppViewModel(
    private val primaryRepository: TransactionRepository,
    private val importStateRepository: ImportStateRepository,
    private val demoRepository: TransactionRepository?,
    private val importRunner: ImportRunner,
    initialPermissionGranted: Boolean,
) : ViewModel() {
    private var repositoryObservation: Job? = null
    private var importObservation: Job? = null
    private var importJob: Job? = null
    private var platformPermissionGranted = initialPermissionGranted
    private var shouldShowPermissionRationale = false
    private var permissionRequested = false
    private var durableImportState = ImportState()
    private var resumeReconciliationPending = false
    private val _uiState = MutableStateFlow(
        AppUiState(
            permission = initialPermissionGranted.toPermissionUiState(),
            demoAvailable = demoRepository != null,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        observe(primaryRepository)
        observeImportState()
    }

    fun onPermissionRequestStarted() {
        permissionRequested = true
        refreshPermissionState()
        viewModelScope.launch {
            importStateRepository.setSmsPermissionRequested(true)
        }
    }

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        updatePlatformPermission(granted, shouldShowRationale)
    }

    fun onAppResumed(granted: Boolean, shouldShowRationale: Boolean) {
        updatePlatformPermission(granted, shouldShowRationale)
        resumeReconciliationPending = granted && !_uiState.value.isScanning
        maybeRunPendingReconciliation()
    }

    fun onDestinationSelected(destination: TopLevelDestination) {
        _uiState.update { it.copy(selectedDestination = destination) }
    }

    fun onScanMessages() {
        if (_uiState.value.permission != PermissionUiState.GRANTED || _uiState.value.isScanning) {
            return
        }
        val mode = if (durableImportState.initialImportComplete) {
            ImportMode.RECONCILIATION
        } else {
            ImportMode.INITIAL
        }
        runImport(mode)
    }

    fun onCancelImport() {
        importJob?.cancel()
    }

    fun onUseDemoData() {
        val repository = demoRepository ?: return
        _uiState.update {
            it.copy(
                stage = AppStage.MAIN,
                selectedDestination = TopLevelDestination.DASHBOARD,
                usingDemoData = true,
                error = null,
            )
        }
        observe(repository)
    }

    fun onLeaveDemoData() {
        _uiState.update {
            it.copy(
                selectedDestination = TopLevelDestination.DASHBOARD,
                usingDemoData = false,
                error = null,
            )
        }
        observe(primaryRepository)
        applyImportState(durableImportState)
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }

    private fun onTransactionsChanged(transactions: List<LedgerTransaction>) {
        val usingDemoData = _uiState.value.usingDemoData
        val includedInr = transactions.filter {
            it.transaction.money.currency == CurrencyCode.INR && it.isIncludedInSpend
        }
        _uiState.update {
            it.copy(
                dashboard = DashboardUiState(
                    inrSpendMinor = includedInr.sumOf { transaction ->
                        transaction.transaction.money.amountMinor
                    },
                    includedTransactions = includedInr.size,
                    foreignTransactions = transactions.count { transaction ->
                        transaction.transaction.money.currency != CurrencyCode.INR
                    },
                    isDemo = usingDemoData,
                ),
                transactions = TransactionsUiState(
                    transactions = transactions,
                    isDemo = usingDemoData,
                ),
            )
        }
    }

    private fun observe(repository: TransactionRepository) {
        // Only one source may drive the UI when switching between real and demo data.
        repositoryObservation?.cancel()
        repositoryObservation = viewModelScope.launch {
            repository.observeTransactions().collectLatest(::onTransactionsChanged)
        }
    }

    private fun observeImportState() {
        importObservation?.cancel()
        importObservation = viewModelScope.launch {
            permissionRequested = importStateRepository.wasSmsPermissionRequested()
            val restoredState = importStateRepository.getImportState()
            if (restoredState.status == ImportRunStatus.RUNNING) {
                // No process-local job can survive restart, so this run is abandoned.
                importStateRepository.saveImportState(
                    restoredState.copy(
                        status = ImportRunStatus.FAILED,
                        failureCode = ImportFailureCode.INTERRUPTED,
                    ),
                )
            }
            importStateRepository.observeImportState().collectLatest { state ->
                durableImportState = state
                if (!state.initialImportComplete && state.status != ImportRunStatus.RUNNING) {
                    // A fresh install consumed this resume event before its first import.
                    resumeReconciliationPending = false
                }
                applyImportState(state)
                refreshPermissionState()
                maybeRunPendingReconciliation()
            }
        }
    }

    private fun runImport(mode: ImportMode) {
        if (mode == ImportMode.INITIAL) {
            // Returning from the permission dialog must not queue a second scan.
            resumeReconciliationPending = false
        }
        _uiState.update {
            it.copy(
                isScanning = true,
                importStatus = ImportRunStatus.RUNNING,
                importProgress = ImportProgress(),
                error = null,
                usingDemoData = false,
            )
        }
        observe(primaryRepository)
        importJob = viewModelScope.launch {
            try {
                val result = importRunner.run(mode) { progress ->
                    _uiState.update { it.copy(importProgress = progress) }
                }
                durableImportState = result
                applyImportState(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        importStatus = ImportRunStatus.FAILED,
                        error = AppError.SCAN_FAILED,
                    )
                }
            }
        }
    }

    private fun applyImportState(state: ImportState) {
        _uiState.update { current ->
            current.copy(
                stage = when {
                    current.usingDemoData -> AppStage.MAIN
                    state.initialImportComplete -> AppStage.MAIN
                    else -> AppStage.ONBOARDING
                },
                isScanning = state.status == ImportRunStatus.RUNNING,
                importStatus = state.status,
                importProgress = state.progress,
                scanSummary = if (state.status == ImportRunStatus.NOT_STARTED) null else {
                    state.progress.toSummary()
                },
                error = if (state.status == ImportRunStatus.FAILED) AppError.SCAN_FAILED else null,
            )
        }
    }

    private fun updatePlatformPermission(granted: Boolean, shouldShowRationale: Boolean) {
        platformPermissionGranted = granted
        shouldShowPermissionRationale = shouldShowRationale
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val permission = when {
            platformPermissionGranted -> PermissionUiState.GRANTED
            durableImportState.initialImportComplete -> PermissionUiState.REVOKED
            !permissionRequested -> PermissionUiState.NOT_REQUESTED
            shouldShowPermissionRationale -> PermissionUiState.DENIED
            else -> PermissionUiState.PERMANENTLY_DENIED
        }
        _uiState.update { it.copy(permission = permission) }
    }

    private fun maybeRunPendingReconciliation() {
        if (!resumeReconciliationPending ||
            !durableImportState.initialImportComplete ||
            !platformPermissionGranted ||
            _uiState.value.isScanning ||
            _uiState.value.usingDemoData
        ) return

        resumeReconciliationPending = false
        runImport(ImportMode.RECONCILIATION)
    }

    /**
     * Supplies constructor dependencies when Android creates [AppViewModel].
     * This keeps framework creation compatible with explicit dependency injection
     * and lets tests instantiate the ViewModel with fakes directly.
     */
    class Factory(
        private val primaryRepository: TransactionRepository,
        private val importStateRepository: ImportStateRepository,
        private val demoRepository: TransactionRepository?,
        private val importRunner: ImportRunner,
        private val initialPermissionGranted: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                "Unsupported ViewModel: ${modelClass.name}"
            }
            return AppViewModel(
                primaryRepository = primaryRepository,
                importStateRepository = importStateRepository,
                demoRepository = demoRepository,
                importRunner = importRunner,
                initialPermissionGranted = initialPermissionGranted,
            ) as T
        }
    }
}

private fun Boolean.toPermissionUiState(): PermissionUiState =
    if (this) PermissionUiState.GRANTED else PermissionUiState.NOT_REQUESTED

private fun ImportProgress.toSummary() = ScanSummaryUiState(
    scannedMessages = scannedMessages,
    recognizedTransactions = recognizedTransactions,
    rejectedMessages = rejectedMessages,
    reviewTransactions = reviewTransactions,
    savedTransactions = savedTransactions,
)
