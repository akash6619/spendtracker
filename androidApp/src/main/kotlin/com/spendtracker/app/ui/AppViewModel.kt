package com.spendtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.MessageScanner
import com.spendtracker.app.data.MessageScanResult
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
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
    private val demoRepository: TransactionRepository?,
    private val messageScanner: MessageScanner,
    initialPermissionGranted: Boolean,
) : ViewModel() {
    private var repositoryObservation: Job? = null
    private val _uiState = MutableStateFlow(
        AppUiState(
            permission = initialPermissionGranted.toPermissionUiState(),
            demoAvailable = demoRepository != null,
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        observe(primaryRepository)
    }

    fun onPermissionChanged(granted: Boolean) {
        _uiState.update {
            it.copy(permission = granted.toPermissionUiState())
        }
    }

    fun onDestinationSelected(destination: TopLevelDestination) {
        _uiState.update { it.copy(selectedDestination = destination) }
    }

    fun onScanMessages() {
        if (_uiState.value.permission != PermissionUiState.GRANTED || _uiState.value.isScanning) {
            return
        }

        _uiState.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            try {
                onScanSucceeded(messageScanner.scanRecentMessages())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(isScanning = false, error = AppError.SCAN_FAILED)
                }
            }
        }
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
                stage = AppStage.ONBOARDING,
                selectedDestination = TopLevelDestination.DASHBOARD,
                usingDemoData = false,
                error = null,
            )
        }
        observe(primaryRepository)
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun onScanSucceeded(result: MessageScanResult) {
        primaryRepository.upsert(result.transactions)
        _uiState.update {
            it.copy(
                stage = AppStage.MAIN,
                selectedDestination = TopLevelDestination.DASHBOARD,
                isScanning = false,
                scanSummary = ScanSummaryUiState(
                    scannedMessages = result.scannedMessages,
                    recognizedTransactions = result.transactions.size,
                ),
                usingDemoData = false,
                error = null,
            )
        }
        observe(primaryRepository)
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

    /**
     * Supplies constructor dependencies when Android creates [AppViewModel].
     * This keeps framework creation compatible with explicit dependency injection
     * and lets tests instantiate the ViewModel with fakes directly.
     */
    class Factory(
        private val primaryRepository: TransactionRepository,
        private val demoRepository: TransactionRepository?,
        private val messageScanner: MessageScanner,
        private val initialPermissionGranted: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(AppViewModel::class.java)) {
                "Unsupported ViewModel: ${modelClass.name}"
            }
            return AppViewModel(
                primaryRepository = primaryRepository,
                demoRepository = demoRepository,
                messageScanner = messageScanner,
                initialPermissionGranted = initialPermissionGranted,
            ) as T
        }
    }
}

private fun Boolean.toPermissionUiState(): PermissionUiState =
    if (this) PermissionUiState.GRANTED else PermissionUiState.REQUIRED
