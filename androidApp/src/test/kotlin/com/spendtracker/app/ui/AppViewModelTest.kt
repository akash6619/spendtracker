package com.spendtracker.app.ui

import com.spendtracker.app.data.InMemoryTransactionRepository
import com.spendtracker.core.importing.ImportFailureCode
import com.spendtracker.core.importing.ImportMode
import com.spendtracker.core.importing.ImportProgress
import com.spendtracker.core.importing.ImportRunStatus
import com.spendtracker.core.importing.ImportRunner
import com.spendtracker.core.importing.ImportState
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.repository.ImportStateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Verifies top-level UI orchestration without Android framework dependencies.
 * Fake repositories and import runners make permission, retry, restoration,
 * reconciliation, and demo-mode transitions deterministic.
 */
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun successfulInitialImportMovesFromOnboardingToDashboard() = runTest {
        val repository = InMemoryTransactionRepository()
        val stateRepository = FakeImportStateRepository()
        val transaction = transaction("synthetic-import")
        val requestedModes = mutableListOf<ImportMode>()
        val runner = ImportRunner { mode, onProgress ->
            requestedModes += mode
            assertEquals(ImportMode.INITIAL, mode)
            val progress = ImportProgress(12, 1, 11, savedTransactions = 1)
            onProgress(progress)
            repository.upsert(listOf(transaction))
            ImportState(
                status = ImportRunStatus.COMPLETED,
                initialImportComplete = true,
                lastSuccessfulScanEpochMillis = 1_788_457_600_000,
                progress = progress,
            ).also { stateRepository.saveImportState(it) }
        }
        val viewModel = viewModel(repository, stateRepository, runner, permissionGranted = false)
        advanceUntilIdle()

        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertEquals(PermissionUiState.NOT_REQUESTED, viewModel.uiState.value.permission)

        viewModel.onPermissionRequestStarted()
        viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
        viewModel.onAppResumed(granted = true, shouldShowRationale = false)
        viewModel.onScanMessages()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AppStage.MAIN, state.stage)
        assertEquals(12, state.scanSummary?.scannedMessages)
        assertEquals(1, state.scanSummary?.savedTransactions)
        assertEquals(685_00, state.dashboard.inrSpendMinor)
        assertEquals(listOf(ImportMode.INITIAL), requestedModes)
        assertFalse(state.isScanning)
    }

    @Test
    fun failedInitialImportCanBeRetried() = runTest {
        val stateRepository = FakeImportStateRepository()
        var attempts = 0
        val runner = ImportRunner { _, _ ->
            attempts += 1
            val state = if (attempts == 1) {
                ImportState(
                    status = ImportRunStatus.FAILED,
                    progress = ImportProgress(scannedMessages = 3, rejectedMessages = 3),
                    failureCode = ImportFailureCode.SOURCE_OR_STORAGE_FAILURE,
                )
            } else {
                ImportState(
                    status = ImportRunStatus.COMPLETED,
                    initialImportComplete = true,
                    progress = ImportProgress(scannedMessages = 3, rejectedMessages = 3),
                )
            }
            stateRepository.saveImportState(state)
            state
        }
        val viewModel = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            runner,
            permissionGranted = true,
        )
        advanceUntilIdle()

        viewModel.onScanMessages()
        advanceUntilIdle()
        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertEquals(AppError.SCAN_FAILED, viewModel.uiState.value.error)

        viewModel.onScanMessages()
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertEquals(AppStage.MAIN, viewModel.uiState.value.stage)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun permissionHistoryDistinguishesDeniedBlockedAndRevoked() = runTest {
        val stateRepository = FakeImportStateRepository()
        val viewModel = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            ImportRunner { _, _ -> ImportState() },
            permissionGranted = false,
        )
        advanceUntilIdle()

        viewModel.onPermissionRequestStarted()
        viewModel.onPermissionResult(granted = false, shouldShowRationale = true)
        advanceUntilIdle()
        assertEquals(PermissionUiState.DENIED, viewModel.uiState.value.permission)
        assertTrue(stateRepository.wasSmsPermissionRequested())

        viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
        assertEquals(PermissionUiState.PERMANENTLY_DENIED, viewModel.uiState.value.permission)

        stateRepository.saveImportState(
            ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true),
        )
        advanceUntilIdle()
        assertEquals(AppStage.MAIN, viewModel.uiState.value.stage)
        assertEquals(PermissionUiState.REVOKED, viewModel.uiState.value.permission)
    }

    @Test
    fun completedImportSkipsOnboardingAndReconcilesOnResume() = runTest {
        val stateRepository = FakeImportStateRepository(
            ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true),
        )
        var mode: ImportMode? = null
        val runner = ImportRunner { requestedMode, _ ->
            mode = requestedMode
            stateRepository.getImportState()
        }
        val viewModel = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            runner,
            permissionGranted = true,
        )
        advanceUntilIdle()
        assertEquals(AppStage.MAIN, viewModel.uiState.value.stage)

        viewModel.onAppResumed(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        assertEquals(ImportMode.RECONCILIATION, mode)
    }

    @Test
    fun staleRunningImportBecomesInterruptedAndCanRetry() = runTest {
        val stateRepository = FakeImportStateRepository(
            ImportState(
                status = ImportRunStatus.RUNNING,
                progress = ImportProgress(scannedMessages = 7, savedTransactions = 2),
            ),
        )
        var attempts = 0
        val runner = ImportRunner { _, _ ->
            attempts += 1
            ImportState(
                status = ImportRunStatus.COMPLETED,
                initialImportComplete = true,
            ).also { stateRepository.saveImportState(it) }
        }
        val viewModel = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            runner,
            permissionGranted = true,
        )

        advanceUntilIdle()
        assertEquals(ImportRunStatus.FAILED, viewModel.uiState.value.importStatus)
        assertEquals(ImportFailureCode.INTERRUPTED, stateRepository.getImportState().failureCode)
        assertFalse(viewModel.uiState.value.isScanning)

        viewModel.onScanMessages()
        advanceUntilIdle()
        assertEquals(1, attempts)
        assertEquals(AppStage.MAIN, viewModel.uiState.value.stage)
    }

    @Test
    fun demoRepositorySupportsNavigationWithoutPermission() = runTest {
        val demoTransaction = transaction("synthetic-demo")
        val viewModel = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(),
            importStateRepository = FakeImportStateRepository(),
            demoRepository = InMemoryTransactionRepository(listOf(demoTransaction)),
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = false,
        )
        advanceUntilIdle()

        viewModel.onUseDemoData()
        advanceUntilIdle()
        viewModel.onDestinationSelected(TopLevelDestination.TRANSACTIONS)

        assertTrue(viewModel.uiState.value.usingDemoData)
        assertEquals(listOf(demoTransaction.transaction), viewModel.uiState.value.transactions.transactions.map { it.transaction })

        viewModel.onLeaveDemoData()
        advanceUntilIdle()
        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.usingDemoData)
    }

    private fun viewModel(
        repository: InMemoryTransactionRepository,
        stateRepository: FakeImportStateRepository,
        runner: ImportRunner,
        permissionGranted: Boolean,
    ) = AppViewModel(
        primaryRepository = repository,
        importStateRepository = stateRepository,
        demoRepository = null,
        importRunner = runner,
        initialPermissionGranted = permissionGranted,
    )

    private fun transaction(sourceId: String) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = sourceId,
        sourceFingerprint = sourceId,
        transaction = ParsedTransaction(
            sourceId = sourceId,
            sourceReceivedAtEpochMillis = 1_788_457_600_000,
            money = Money(685_00, CurrencyCode.INR),
            direction = TransactionDirection.DEBIT,
            kind = TransactionKind.PURCHASE,
            category = SpendCategory.FOOD_AND_DINING,
            merchant = "Northstar Cafe",
            accountHint = null,
            confidence = 0.90,
            parserVersion = 1,
        ),
    )
}

/**
 * Mutable substitute for Room's singleton import and settings rows.
 * It emits every saved state immediately so ViewModel observation behaves like
 * production while tests retain direct control over starting state and history.
 */
private class FakeImportStateRepository(
    initialState: ImportState = ImportState(),
) : ImportStateRepository {
    private val state = MutableStateFlow(initialState)
    private var permissionRequested = false

    override fun observeImportState(): Flow<ImportState> = state
    override suspend fun getImportState(): ImportState = state.value
    override suspend fun saveImportState(state: ImportState) { this.state.value = state }
    override suspend fun wasSmsPermissionRequested(): Boolean = permissionRequested
    override suspend fun setSmsPermissionRequested(requested: Boolean) { permissionRequested = requested }
}
