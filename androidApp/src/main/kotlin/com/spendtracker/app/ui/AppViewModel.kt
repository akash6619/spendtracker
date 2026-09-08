package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.SourceLookupResult
import com.spendtracker.app.data.SourceMessageLookup
import com.spendtracker.app.data.SourceUnavailableReason
import com.spendtracker.core.aggregation.DashboardPeriod
import com.spendtracker.core.aggregation.PeriodCalculator
import com.spendtracker.core.aggregation.ReportAggregator
import com.spendtracker.core.importing.ImportMode
import com.spendtracker.core.importing.ImportFailureCode
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.ImportRunner
import com.spendtracker.core.importing.ImportState
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionFilter
import com.spendtracker.core.model.filteredBy
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
import kotlinx.datetime.TimeZone
import java.time.ZoneId

/**
 * Owns the top-level application state and user-intent orchestration.
 *
 * It tracks permission and navigation, runs scans, writes accepted candidates to
 * the production repository, and derives dashboard/list state from the active
 * observable repository. Dashboard facts come from the shared aggregator using
 * an injected clock and device time zone, so period totals are deterministic
 * and recompute when the device zone changes. Debug demo mode temporarily swaps
 * the active source.
 */
class AppViewModel(
    private val primaryRepository: TransactionRepository,
    private val importStateRepository: ImportStateRepository,
    private val demoRepository: TransactionRepository?,
    private val importRunner: ImportRunner,
    initialPermissionGranted: Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = { TimeZone.of(ZoneId.systemDefault().id) },
    private val sourceMessageLookup: SourceMessageLookup? = null,
    private val deleteAllLocalData: suspend () -> Unit = {},
) : ViewModel() {
    private var repositoryObservation: Job? = null
    private var activeRepository = primaryRepository
    private var ledger: List<LedgerTransaction> = emptyList()
    private var editJob: Job? = null
    private var sourceJob: Job? = null
    private var importObservation: Job? = null
    private var importJob: Job? = null
    private var deleteJob: Job? = null
    private var platformPermissionGranted = initialPermissionGranted
    private var shouldShowPermissionRationale = false
    private var permissionRequested = false
    private var durableImportState = ImportState()
    private var resumeReconciliationPending = false
    private var autoInitialAttempted = false
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
        // Once SMS access is granted and no initial import has run, start the
        // three-month scan automatically without an explicit button.
        maybeAutoStartInitialImport()
    }

    fun onAppResumed(granted: Boolean, shouldShowRationale: Boolean) {
        updatePlatformPermission(granted, shouldShowRationale)
        // A backgrounded app may resume in a new time zone; period windows follow
        // the device zone, so recompute dashboard facts from the same stored data.
        _uiState.update {
            it.copy(
                dashboard = computeDashboard(
                    ledger,
                    it.dashboard.period,
                    it.dashboard.periodOffset,
                    it.usingDemoData,
                ),
            )
        }
        resumeReconciliationPending = granted && !_uiState.value.isScanning
        maybeAutoStartInitialImport()
        maybeRunPendingReconciliation()
    }

    fun onDestinationSelected(destination: TopLevelDestination) {
        _uiState.update { it.copy(selectedDestination = destination) }
    }

    fun onDashboardPeriodSelected(period: DashboardPeriod) {
        _uiState.update { it.copy(dashboard = computeDashboard(ledger, period, 0, it.usingDemoData)) }
    }

    fun onDashboardPreviousPeriod() {
        _uiState.update {
            val offset = it.dashboard.periodOffset - 1
            it.copy(dashboard = computeDashboard(ledger, it.dashboard.period, offset, it.usingDemoData))
        }
    }

    fun onDashboardNextPeriod() {
        _uiState.update {
            val offset = minOf(0, it.dashboard.periodOffset + 1)
            it.copy(dashboard = computeDashboard(ledger, it.dashboard.period, offset, it.usingDemoData))
        }
    }

    fun onDashboardCategorySelected(category: SpendCategory) =
        openDashboardTransactions(TransactionFilter(category = category, currency = CurrencyCode.INR))

    fun onDashboardExcludedSelected() =
        openDashboardTransactions(TransactionFilter(included = false, currency = CurrencyCode.INR))

    fun onDashboardForeignSelected() = openDashboardTransactions(TransactionFilter(foreignOnly = true))

    fun onTransactionFilterChanged(filter: TransactionFilter) {
        _uiState.update { it.copy(transactions = it.transactions.copy(
            filter = filter, transactions = ledger.filteredBy(filter),
        )) }
    }

    fun onTransactionSelected(id: String) {
        dismissSourceView()
        _uiState.update { it.copy(transactions = it.transactions.copy(
            selected = ledger.firstOrNull { row -> row.id == id },
            saveFailed = false, saveSucceeded = false,
        )) }
    }

    fun onTransactionClosed() {
        if (_uiState.value.transactions.isSaving) return
        dismissSourceView()
        _uiState.update { it.copy(transactions = it.transactions.copy(selected = null)) }
    }

    fun onViewSourceMessage() {
        val selected = _uiState.value.transactions.selected ?: return
        if (_uiState.value.transactions.sourceView == SourceViewUiState.Loading) return
        dismissSourceView()
        val lookup = sourceMessageLookup
        val permissionGranted = platformPermissionGranted
        val sourceView = when {
            lookup == null -> SourceViewUiState.Unavailable(SourceUnavailableReason.LOOKUP_FAILED)
            !permissionGranted -> SourceViewUiState.Unavailable(SourceUnavailableReason.PERMISSION_REVOKED)
            else -> null
        }
        if (sourceView != null) {
            _uiState.update { it.copy(transactions = it.transactions.copy(sourceView = sourceView)) }
            return
        }
        _uiState.update { it.copy(transactions = it.transactions.copy(sourceView = SourceViewUiState.Loading)) }
        sourceJob = viewModelScope.launch {
            try {
                val result = lookup!!.lookup(selected)
                _uiState.update {
                    it.copy(transactions = it.transactions.copy(
                        sourceView = when (result) {
                            is SourceLookupResult.Found -> SourceViewUiState.Found(
                                sender = result.sender,
                                body = result.body,
                                receivedAtEpochMillis = result.receivedAtEpochMillis,
                            )
                            is SourceLookupResult.Unavailable -> SourceViewUiState.Unavailable(result.reason)
                        },
                    ))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(transactions = it.transactions.copy(
                        sourceView = SourceViewUiState.Unavailable(SourceUnavailableReason.LOOKUP_FAILED),
                    ))
                }
            }
        }
    }

    fun onDismissSourceMessage() = dismissSourceView()

    private fun dismissSourceView() {
        sourceJob?.cancel()
        sourceJob = null
        _uiState.update { it.copy(transactions = it.transactions.copy(sourceView = null)) }
    }

    fun onRetryTransactions() = observe(activeRepository)

    fun onSaveTransaction(category: SpendCategory, included: Boolean) {
        val selected = _uiState.value.transactions.selected ?: return
        if (_uiState.value.transactions.isSaving) return
        val repository = activeRepository
        _uiState.update { it.copy(transactions = it.transactions.copy(
            isSaving = true, saveFailed = false, saveSucceeded = false,
        )) }
        editJob = viewModelScope.launch {
            try {
                checkNotNull(repository.getById(selected.id))
                repository.updateTransaction(selected.id, category, included)
                // Read back before acknowledging the save; repository observation may lag.
                val saved = checkNotNull(repository.getById(selected.id))
                onTransactionsChanged(ledger.map { if (it.id == saved.id) saved else it })
                _uiState.update { it.copy(transactions = it.transactions.copy(
                    isSaving = false, saveSucceeded = true,
                )) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(transactions = it.transactions.copy(
                    isSaving = false, saveFailed = true,
                )) }
            }
        }
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

    fun onDeleteAllNoticeShown() {
        _uiState.update { it.copy(settings = it.settings.copy(deleteAllSucceeded = false)) }
    }

    fun onRequestDeleteAll() {
        if (_uiState.value.settings.isDeletingAll || _uiState.value.isScanning) return
        _uiState.update { it.copy(settings = it.settings.copy(showDeleteConfirm = true)) }
    }

    fun onCancelDeleteAll() {
        _uiState.update { it.copy(settings = it.settings.copy(showDeleteConfirm = false)) }
    }

    fun onConfirmDeleteAll() {
        val settings = _uiState.value.settings
        if (settings.isDeletingAll) return
        _uiState.update { it.copy(settings = it.settings.copy(
            showDeleteConfirm = false, isDeletingAll = true, deleteAllFailed = false, deleteAllSucceeded = false,
        )) }
        deleteJob = viewModelScope.launch {
            try {
                importJob?.cancel()
                deleteAllLocalData()
                resetAfterDeleteAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(settings = it.settings.copy(
                    isDeletingAll = false, deleteAllFailed = true,
                )) }
            }
        }
    }

    /**
     * Wipes in-memory orchestration state after local data deletion and returns
     * to onboarding. SMS system permission is unchanged; a re-scan only happens
     * on a later explicit grant flow or app resume.
     */
    private fun resetAfterDeleteAll() {
        importJob?.cancel()
        editJob?.cancel()
        sourceJob?.cancel()
        repositoryObservation?.cancel()
        importObservation?.cancel()
        ledger = emptyList()
        durableImportState = ImportState()
        permissionRequested = false
        autoInitialAttempted = false
        resumeReconciliationPending = false
        activeRepository = primaryRepository
        _uiState.value = AppUiState(
            permission = if (platformPermissionGranted) PermissionUiState.GRANTED else PermissionUiState.NOT_REQUESTED,
            demoAvailable = demoRepository != null,
            settings = SettingsUiState(deleteAllSucceeded = true, isDeletingAll = false),
        )
        observe(primaryRepository)
        observeImportState()
        applyImportState(durableImportState)
    }

    private fun onTransactionsChanged(transactions: List<LedgerTransaction>) {
        ledger = transactions
        val usingDemoData = _uiState.value.usingDemoData
        _uiState.update {
            it.copy(
                dashboard = computeDashboard(
                    transactions, it.dashboard.period, it.dashboard.periodOffset, usingDemoData,
                ),
                settings = it.settings.copy(
                    storedTransactionCount = if (usingDemoData) it.settings.storedTransactionCount else transactions.size,
                ),
                transactions = it.transactions.copy(
                    transactions = transactions.filteredBy(it.transactions.filter),
                    isDemo = usingDemoData,
                    selected = it.transactions.selected?.let { selected ->
                        transactions.firstOrNull { row -> row.id == selected.id }
                    },
                    isLoading = false,
                    loadFailed = false,
                ),
            )
        }
    }

    /**
     * Deep links a dashboard fact to the transaction list with a filter that
     * reproduces the tapped count: same period window plus the tapped dimension.
     */
    private fun openDashboardTransactions(filter: TransactionFilter) {
        val range = _uiState.value.dashboard.report?.range ?: return
        onTransactionFilterChanged(filter.copy(
            fromInclusive = range.startInclusiveEpochMillis,
            toExclusive = range.endExclusiveEpochMillis,
        ))
        _uiState.update { it.copy(selectedDestination = TopLevelDestination.TRANSACTIONS) }
    }

    private fun computeDashboard(
        transactions: List<LedgerTransaction>,
        period: DashboardPeriod,
        periodOffset: Int,
        isDemo: Boolean,
    ): DashboardUiState {
        if (transactions.isEmpty()) {
            return DashboardUiState(period = period, periodOffset = periodOffset, isDemo = isDemo)
        }
        val zone = timeZone()
        val now = nowEpochMillis()
        val currentRange = PeriodCalculator.rangeAtOffset(now, zone, period, periodOffset)
        val report = ReportAggregator.compute(transactions, currentRange, period, zone)
        val previousRange = PeriodCalculator.comparisonRange(now, zone, period, periodOffset)
        val previous = ReportAggregator.compute(transactions, previousRange, period, zone)
        val comparison = ReportAggregator.compare(report.includedInrTotalMinor, previous.includedInrTotalMinor)
        return DashboardUiState(
            period = period,
            periodOffset = periodOffset,
            report = report,
            comparison = comparison,
            hasTransactions = true,
            isDemo = isDemo,
        )
    }

    private fun observe(repository: TransactionRepository) {
        // Only one source may drive the UI when switching between real and demo data.
        repositoryObservation?.cancel()
        if (activeRepository !== repository) {
            editJob?.cancel()
            ledger = emptyList()
            _uiState.update { it.copy(transactions = TransactionsUiState()) }
        }
        activeRepository = repository
        _uiState.update { it.copy(transactions = it.transactions.copy(isLoading = true, loadFailed = false)) }
        repositoryObservation = viewModelScope.launch {
            try {
                repository.observeTransactions().collectLatest(::onTransactionsChanged)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(transactions = it.transactions.copy(isLoading = false, loadFailed = true)) }
            }
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
                settings = current.settings.copy(
                    lastScanEpochMillis = state.lastSuccessfulScanEpochMillis,
                    parserVersion = state.parserVersion,
                ),
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
     * Starts the three-month initial scan automatically once SMS access is
     * granted and no initial import has completed, with no explicit button.
     * It fires at most once per grant; a failed attempt needs the retry action.
     */
    private fun maybeAutoStartInitialImport() {
        if (_uiState.value.usingDemoData ||
            !platformPermissionGranted ||
            durableImportState.initialImportComplete ||
            _uiState.value.isScanning ||
            autoInitialAttempted
        ) return

        autoInitialAttempted = true
        runImport(ImportMode.INITIAL)
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
        private val sourceMessageLookup: SourceMessageLookup,
        private val deleteAllLocalData: suspend () -> Unit,
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
                sourceMessageLookup = sourceMessageLookup,
                deleteAllLocalData = deleteAllLocalData,
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
