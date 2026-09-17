package com.spendtracker.app.ui

import com.spendtracker.app.data.InMemoryTransactionRepository
import com.spendtracker.app.data.SourceLookupResult
import com.spendtracker.app.data.SourceMessageLookup
import com.spendtracker.app.data.SourceUnavailableReason
import com.spendtracker.core.aggregation.DashboardPeriod
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
import com.spendtracker.core.model.TransactionFilter
import com.spendtracker.core.model.TransactionReviewReason
import com.spendtracker.core.model.needsReview
import com.spendtracker.core.repository.TransactionRepository
import com.spendtracker.core.repository.ImportStateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * Verifies top-level UI orchestration without Android framework dependencies.
 * Fake repositories and import runners make permission, retry, restoration,
 * reconciliation, demo-mode, dashboard aggregation, and deep-link transitions
 * deterministic. Dashboard tests inject a fixed clock and UTC zone so calendar
 * windows never depend on the host machine's time zone.
 */
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Wednesday 2026-09-09, inside the UTC week starting Monday 2026-09-07.
    private val fixedNow: Long = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli()

    @Test
    fun editsUpdateFilteredListAndDashboardWithoutClosingDetailAndCanSaveAgain() = runTest {
        val repository = InMemoryTransactionRepository(listOf(transaction("edit")))
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, false)
        advanceUntilIdle()
        val id = vm.uiState.value.transactions.transactions.single().id
        vm.onTransactionFilterChanged(TransactionFilter(category = SpendCategory.FOOD_AND_DINING, included = true))
        vm.onTransactionSelected(id)
        vm.onUpdateTransaction("Harbor Books", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.TRAVEL, false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transactions.transactions.isEmpty())
        assertEquals(SpendCategory.TRAVEL, vm.uiState.value.transactions.selected?.category)
        assertEquals("Harbor Books", vm.uiState.value.transactions.selected?.merchant)
        assertEquals(0, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        assertTrue(vm.uiState.value.transactions.saveSucceeded)
        vm.onUpdateTransaction("Harbor Books", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.FOOD_AND_DINING, true)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.transactions.transactions.size)
        assertEquals(685_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        vm.onTransactionClosed()
        assertEquals(null, vm.uiState.value.transactions.selected)
    }

    @Test
    fun failedEditRetainsStoredValuesAndCanRetry() = runTest {
        val backing = InMemoryTransactionRepository(listOf(transaction("edit")))
        var fail = true
        val repository = object : TransactionRepository by backing {
            override suspend fun updateTransaction(
                id: String,
                merchant: String?,
                kind: TransactionKind,
                direction: TransactionDirection,
                category: SpendCategory,
                includedInSpend: Boolean,
            ) {
                if (fail) error("synthetic failure")
                backing.updateTransaction(id, merchant, kind, direction, category, includedInSpend)
            }
        }
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, false)
        advanceUntilIdle()
        vm.onTransactionSelected(vm.uiState.value.transactions.transactions.single().id)
        vm.onUpdateTransaction("Northstar Cafe", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.TRAVEL, false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transactions.saveFailed)
        assertEquals(685_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        fail = false
        vm.onUpdateTransaction("Northstar Cafe", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.TRAVEL, false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.transactions.saveFailed)
        assertEquals(0, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
    }

    @Test
    fun observationFailureCanRetryAndClearFilters() = runTest {
        val backing = InMemoryTransactionRepository(listOf(transaction("retry")))
        var fail = true
        val repository = object : TransactionRepository by backing {
            override fun observeTransactions() = if (fail) kotlinx.coroutines.flow.flow { error("synthetic") } else backing.observeTransactions()
        }
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, false)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transactions.loadFailed)
        fail = false
        vm.onRetryTransactions()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.transactions.loadFailed)
        vm.onTransactionFilterChanged(TransactionFilter(currency = CurrencyCode.JPY))
        assertTrue(vm.uiState.value.transactions.transactions.isEmpty())
        vm.onTransactionFilterChanged(TransactionFilter())
        assertEquals(1, vm.uiState.value.transactions.transactions.size)
    }

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
                lastSuccessfulScanEpochMillis = fixedNow,
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
        assertEquals(685_00, state.dashboard.report?.includedInrTotalMinor)
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
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
        )
        advanceUntilIdle()

        viewModel.onUseDemoData()
        advanceUntilIdle()
        viewModel.onDestinationSelected(TopLevelDestination.TRANSACTIONS)

        assertTrue(viewModel.uiState.value.usingDemoData)
        assertEquals(listOf(demoTransaction.sourceFingerprint), viewModel.uiState.value.transactions.transactions.map { it.sourceFingerprint })

        viewModel.onLeaveDemoData()
        advanceUntilIdle()
        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.usingDemoData)
    }

    @Test
    fun dashboardComputesCurrentWeekTotalsAndCounts() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(
                transaction("in-week", timestamp = "2026-09-08T08:00:00Z"),
                transaction("in-week-2", timestamp = "2026-09-10T08:00:00Z", amountMinor = 315_00),
                transaction(
                    "foreign", timestamp = "2026-09-09T09:00:00Z",
                    money = Money(900, CurrencyCode.USD),
                ),
                transaction("last-week", timestamp = "2026-09-02T08:00:00Z", amountMinor = 999_00),
            ),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        val dashboard = vm.uiState.value.dashboard
        assertEquals(1_000_00, dashboard.report?.includedInrTotalMinor)
        assertEquals(2, dashboard.report?.includedInrCount)
        assertEquals(1, dashboard.report?.foreignCount)
        assertEquals(DashboardPeriod.WEEK, dashboard.period)
    }

    @Test
    fun dashboardPeriodSwitchRecomputesMonthWindow() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(
                transaction("this-week", timestamp = "2026-09-08T08:00:00Z"),
                transaction("month-early", timestamp = "2026-09-01T08:00:00Z", amountMinor = 315_00),
            ),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        // The week window (7-13 Sep) excludes the 1 Sep record.
        assertEquals(685_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)

        vm.onDashboardPeriodSelected(DashboardPeriod.MONTH)
        assertEquals(DashboardPeriod.MONTH, vm.uiState.value.dashboard.period)
        assertEquals(1_000_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        // The comparison object exists even with an empty previous baseline.
        assertNotNull(vm.uiState.value.dashboard.comparison)
        assertNull(vm.uiState.value.dashboard.comparison?.deltaPercent)
    }

    @Test
    fun dashboardCanBrowseDaysAndMultipleHistoricalWeeksWithoutEnteringFuture() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(
                transaction("today", timestamp = "2026-09-09T08:00:00Z", amountMinor = 100_00),
                transaction("previous-week", timestamp = "2026-09-01T08:00:00Z", amountMinor = 300_00),
                transaction("two-weeks-back", timestamp = "2026-08-25T08:00:00Z", amountMinor = 200_00),
            ),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        vm.onDashboardPeriodSelected(DashboardPeriod.DAY)
        assertEquals(100_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        assertEquals(0, vm.uiState.value.dashboard.periodOffset)

        vm.onDashboardPeriodSelected(DashboardPeriod.WEEK)
        vm.onDashboardPreviousPeriod()
        assertEquals(-1, vm.uiState.value.dashboard.periodOffset)
        assertEquals(300_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
        assertEquals(200_00, vm.uiState.value.dashboard.comparison?.previousTotalMinor)

        vm.onDashboardPreviousPeriod()
        assertEquals(-2, vm.uiState.value.dashboard.periodOffset)
        assertEquals(200_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)

        vm.onDashboardNextPeriod()
        vm.onDashboardNextPeriod()
        vm.onDashboardNextPeriod()
        assertEquals(0, vm.uiState.value.dashboard.periodOffset)
        assertEquals(100_00, vm.uiState.value.dashboard.report?.includedInrTotalMinor)
    }

    @Test
    fun historicalDashboardDeepLinkUsesTheSelectedRange() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(transaction("previous-week", timestamp = "2026-09-01T08:00:00Z")),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        vm.onDashboardPreviousPeriod()
        val selectedRange = vm.uiState.value.dashboard.report!!.range
        vm.onDashboardCategorySelected(SpendCategory.FOOD_AND_DINING)

        assertEquals(selectedRange.startInclusiveEpochMillis, vm.uiState.value.transactions.filter.fromInclusive)
        assertEquals(selectedRange.endExclusiveEpochMillis, vm.uiState.value.transactions.filter.toExclusive)
        assertEquals(listOf("previous-week"), vm.uiState.value.transactions.transactions.map { it.sourceProviderId })
    }

    @Test
    fun dashboardComparisonPercentageUsesPreviousSameElapsedDays() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(
                transaction("current", timestamp = "2026-09-08T08:00:00Z"),
                transaction("previous-monday", timestamp = "2026-08-31T08:00:00Z", amountMinor = 325_00),
            ),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        // Same elapsed days through Wednesday: 685 vs 325 → +110.77%, rounded to +111.
        assertEquals(111, vm.uiState.value.dashboard.comparison?.deltaPercent)
        assertEquals(360_00, vm.uiState.value.dashboard.comparison?.deltaMinor)
    }

    @Test
    fun categoryDeepLinkOpensTransactionsWithPeriodFilter() = runTest {
        val repository = InMemoryTransactionRepository(listOf(transaction("deep")))
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        vm.onDashboardCategorySelected(SpendCategory.FOOD_AND_DINING)
        val state = vm.uiState.value
        assertEquals(TopLevelDestination.TRANSACTIONS, state.selectedDestination)
        val filter = state.transactions.filter
        assertEquals(SpendCategory.FOOD_AND_DINING, filter.category)
        val range = state.dashboard.report!!.range
        assertEquals(range.startInclusiveEpochMillis, filter.fromInclusive)
        assertEquals(range.endExclusiveEpochMillis, filter.toExclusive)
        assertEquals(listOf("deep"), state.transactions.transactions.map { it.sourceProviderId })
    }

    @Test
    fun excludedForeignAndReviewDeepLinksSetMatchingFilters() = runTest {
        val repository = InMemoryTransactionRepository(
            listOf(
                transaction("plain"),
                transaction(
                    "foreign", timestamp = "2026-09-09T09:00:00Z",
                    money = Money(900, CurrencyCode.USD),
                ),
            ),
        )
        val vm = viewModel(repository, FakeImportStateRepository(), ImportRunner { _, _ -> ImportState() }, true)
        advanceUntilIdle()

        vm.onDashboardExcludedSelected()
        assertEquals(false, vm.uiState.value.transactions.filter.included)
        assertEquals(TopLevelDestination.TRANSACTIONS, vm.uiState.value.selectedDestination)

        vm.onDashboardForeignSelected()
        assertTrue(vm.uiState.value.transactions.filter.foreignOnly)
        assertEquals(1, vm.uiState.value.transactions.transactions.size)
    }

    @Test
    fun timeZoneChangeRecomputesRangesOnResumeWithoutDataLoss() = runTest {
        val repository = InMemoryTransactionRepository(listOf(transaction("tz")))
        var zone: TimeZone = TimeZone.UTC
        val vm = AppViewModel(
            primaryRepository = repository,
            importStateRepository = FakeImportStateRepository(
                ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true),
            ),
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = false,
            nowEpochMillis = { fixedNow },
            timeZone = { zone },
        )
        advanceUntilIdle()
        val utcStart = vm.uiState.value.dashboard.report!!.range.startInclusiveEpochMillis
        assertEquals(1, vm.uiState.value.dashboard.report!!.includedInrCount)

        zone = TimeZone.of("Asia/Kolkata")
        vm.onAppResumed(granted = false, shouldShowRationale = false)
        advanceUntilIdle()

        val kolkataStart = vm.uiState.value.dashboard.report!!.range.startInclusiveEpochMillis
        assertEquals(kolkataStart + 5 * 3_600_000L + 30 * 60_000L, utcStart)
        // The stored transaction still contributes; only the window moved.
        assertEquals(1, vm.uiState.value.dashboard.report!!.includedInrCount)
    }

    @Test
    fun openingDetailLoadsSourceMessageByIdAndClosingClearsIt() = runTest {
        val lookupCalls = mutableListOf<String>()
        val vm = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(listOf(transaction("source"))),
            importStateRepository = FakeImportStateRepository(),
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = true,
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
            sourceMessageLookup = SourceMessageLookup { row ->
                lookupCalls += row.id
                SourceLookupResult.Found("SYNTHETIC SENDER", "SYNTHETIC MESSAGE BODY", fixedNow)
            },
        )
        advanceUntilIdle()
        vm.onTransactionSelected(vm.uiState.value.transactions.transactions.single().id)
        advanceUntilIdle()

        val source = vm.uiState.value.transactions.sourceView
        assertTrue(source is SourceViewUiState.Found)
        assertEquals("SYNTHETIC MESSAGE BODY", (source as SourceViewUiState.Found).body)
        assertEquals(listOf("ANDROID_SMS:source"), lookupCalls)

        vm.onTransactionClosed()
        assertNull(vm.uiState.value.transactions.sourceView)
    }

    @Test
    fun sourceViewWithoutPermissionExplainsRevocationWithoutLookup() = runTest {
        var lookupCalls = 0
        val vm = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(listOf(transaction("source"))),
            importStateRepository = FakeImportStateRepository(),
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = false,
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
            sourceMessageLookup = SourceMessageLookup { _ -> lookupCalls += 1; SourceLookupResult.Unavailable(SourceUnavailableReason.LOOKUP_FAILED) },
        )
        advanceUntilIdle()
        vm.onTransactionSelected(vm.uiState.value.transactions.transactions.single().id)

        assertEquals(
            SourceViewUiState.Unavailable(SourceUnavailableReason.PERMISSION_REVOKED),
            vm.uiState.value.transactions.sourceView,
        )
        assertEquals(0, lookupCalls)
    }

    @Test
    fun sourceViewClearsWhenDetailClosesOrSelectionChanges() = runTest {
        val vm = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(listOf(transaction("source"))),
            importStateRepository = FakeImportStateRepository(),
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = true,
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
            sourceMessageLookup = SourceMessageLookup { _ -> SourceLookupResult.Found("S", "SYNTHETIC BODY", fixedNow) },
        )
        advanceUntilIdle()
        val id = vm.uiState.value.transactions.transactions.single().id
        vm.onTransactionSelected(id)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.transactions.sourceView)

        vm.onTransactionSelected(id)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.transactions.sourceView)
        vm.onTransactionClosed()
        assertNull(vm.uiState.value.transactions.selected)
        assertNull(vm.uiState.value.transactions.sourceView)
    }

    @Test
    fun savingCategoryResolvesOnlyTheCategoryReviewConcern() = runTest {
        val uncertain = transaction("review").copy(
            transaction = transaction("review").transaction.copy(
                category = SpendCategory.OTHER,
                reviewReasons = setOf(TransactionReviewReason.UNKNOWN_CATEGORY),
                confidence = 0.60,
            ),
        )
        val vm = viewModel(
            InMemoryTransactionRepository(listOf(uncertain)),
            FakeImportStateRepository(),
            ImportRunner { _, _ -> ImportState() },
            true,
        )
        advanceUntilIdle()
        val row = vm.uiState.value.transactions.transactions.single()
        assertTrue(row.needsReview)

        vm.onTransactionSelected(row.id)
        vm.onUpdateTransaction(row.merchant, row.kind, row.direction, SpendCategory.SHOPPING, true)
        advanceUntilIdle()

        val saved = vm.uiState.value.transactions.selected!!
        assertFalse(saved.needsReview)
        assertEquals(0.90, saved.confidence)
        assertTrue(saved.reviewReasons.isEmpty())
        assertEquals(SpendCategory.SHOPPING, saved.category)
    }

    @Test
    fun saveKeepsUnresolvedAmountConflictsInReview() = runTest {
        val conflicting = transaction("conflict").copy(
            transaction = transaction("conflict").transaction.copy(
                reviewReasons = setOf(TransactionReviewReason.CONFLICTING_AMOUNTS),
                confidence = 0.35,
            ),
        )
        val vm = viewModel(
            InMemoryTransactionRepository(listOf(conflicting)),
            FakeImportStateRepository(),
            ImportRunner { _, _ -> ImportState() },
            true,
        )
        advanceUntilIdle()
        vm.onTransactionSelected(vm.uiState.value.transactions.transactions.single().id)
        vm.onUpdateTransaction("Northstar Cafe", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.SHOPPING, true)
        advanceUntilIdle()

        val saved = vm.uiState.value.transactions.selected!!
        assertTrue(saved.needsReview)
        assertTrue(TransactionReviewReason.CONFLICTING_AMOUNTS in saved.reviewReasons)
        assertEquals(0.35, saved.confidence)
    }

    @Test
    fun secondSaveKeepsAlreadyResolvedReviewState() = runTest {
        val uncertain = transaction("reset").copy(
            transaction = transaction("reset").transaction.copy(
                category = SpendCategory.OTHER,
                reviewReasons = setOf(TransactionReviewReason.UNKNOWN_CATEGORY),
                confidence = 0.60,
            ),
        )
        val vm = viewModel(
            InMemoryTransactionRepository(listOf(uncertain)),
            FakeImportStateRepository(),
            ImportRunner { _, _ -> ImportState() },
            true,
        )
        advanceUntilIdle()
        vm.onTransactionSelected(vm.uiState.value.transactions.transactions.single().id)
        vm.onUpdateTransaction("Northstar Cafe", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.SHOPPING, true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.transactions.selected!!.needsReview)

        vm.onUpdateTransaction("Northstar Cafe", TransactionKind.PURCHASE, TransactionDirection.DEBIT, SpendCategory.GROCERIES, false)
        advanceUntilIdle()
        val saved = vm.uiState.value.transactions.selected!!
        assertFalse(saved.needsReview)
        assertTrue(saved.reviewReasons.isEmpty())
        assertEquals(0.90, saved.confidence)
        assertEquals(SpendCategory.GROCERIES, saved.category)
        assertFalse(saved.includedInSpend)
    }

    @Test
    fun grantingPermissionAutoStartsInitialImportWithoutManualScan() = runTest {
        val stateRepository = FakeImportStateRepository()
        val requestedModes = mutableListOf<ImportMode>()
        val runner = ImportRunner { mode, _ ->
            requestedModes += mode
            val progress = ImportProgress(scannedMessages = 5)
            ImportState(
                status = ImportRunStatus.COMPLETED,
                initialImportComplete = true,
                progress = progress,
            ).also { stateRepository.saveImportState(it) }
        }
        val vm = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            runner,
            permissionGranted = false,
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transactions.transactions.isEmpty())

        // No explicit scan call: granting access must launch the initial import.
        vm.onPermissionResult(granted = true, shouldShowRationale = false)
        advanceUntilIdle()

        assertEquals(listOf(ImportMode.INITIAL), requestedModes)
        assertEquals(AppStage.MAIN, vm.uiState.value.stage)
    }

    @Test
    fun autoInitialImportDoesNotRepeatOnResumeAfterCompletion() = runTest {
        val stateRepository = FakeImportStateRepository(
            ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true),
        )
        var runs = 0
        val runner = ImportRunner { mode, _ ->
            runs += 1
            assertEquals(ImportMode.RECONCILIATION, mode)
            stateRepository.getImportState()
        }
        val vm = viewModel(
            InMemoryTransactionRepository(),
            stateRepository,
            runner,
            permissionGranted = true,
        )
        advanceUntilIdle()
        vm.onAppResumed(granted = true, shouldShowRationale = false)
        advanceUntilIdle()
        // After completion only reconciliation runs (once), never another initial scan.
        assertEquals(1, runs)
    }

    @Test
    fun deleteAllConfirmWipesLocalStateAndReturnsToOnboarding() = runTest {
        val repository = InMemoryTransactionRepository(listOf(transaction("data")))
        val stateRepository = FakeImportStateRepository(
            ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true, parserVersion = 3),
        )
        var deleted = 0
        val vm = AppViewModel(
            primaryRepository = repository,
            importStateRepository = stateRepository,
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = true,
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
            deleteAllLocalData = {
                deleted += 1
                repository.clear()
                stateRepository.saveImportState(ImportState())
            },
        )
        advanceUntilIdle()
        assertEquals(AppStage.MAIN, vm.uiState.value.stage)
        assertEquals(1, vm.uiState.value.settings.storedTransactionCount)

        vm.onRequestDeleteAll()
        assertTrue(vm.uiState.value.settings.showDeleteConfirm)
        vm.onConfirmDeleteAll()
        advanceUntilIdle()

        assertEquals(1, deleted)
        assertEquals(AppStage.ONBOARDING, vm.uiState.value.stage)
        assertEquals(0, vm.uiState.value.settings.storedTransactionCount)
        assertTrue(vm.uiState.value.transactions.transactions.isEmpty())
        assertTrue(vm.uiState.value.settings.deleteAllSucceeded)
        vm.onDeleteAllNoticeShown()
        assertFalse(vm.uiState.value.settings.deleteAllSucceeded)
    }

    @Test
    fun deleteAllCancelDoesNothingAndFailureIsRecoverable() = runTest {
        val repository = InMemoryTransactionRepository(listOf(transaction("data")))
        val stateRepository = FakeImportStateRepository(
            ImportState(status = ImportRunStatus.COMPLETED, initialImportComplete = true),
        )
        var fail = true
        val vm = AppViewModel(
            primaryRepository = repository,
            importStateRepository = stateRepository,
            demoRepository = null,
            importRunner = ImportRunner { _, _ -> ImportState() },
            initialPermissionGranted = true,
            nowEpochMillis = { fixedNow },
            timeZone = { TimeZone.UTC },
            deleteAllLocalData = {
                if (fail) error("synthetic delete failure")
                repository.clear()
                stateRepository.saveImportState(ImportState())
            },
        )
        advanceUntilIdle()
        vm.onRequestDeleteAll()
        vm.onCancelDeleteAll()
        assertFalse(vm.uiState.value.settings.showDeleteConfirm)
        assertEquals(AppStage.MAIN, vm.uiState.value.stage)

        vm.onRequestDeleteAll()
        vm.onConfirmDeleteAll()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.settings.deleteAllFailed)
        assertEquals(AppStage.MAIN, vm.uiState.value.stage)

        fail = false
        vm.onConfirmDeleteAll()
        advanceUntilIdle()
        assertEquals(AppStage.ONBOARDING, vm.uiState.value.stage)
    }

    private fun viewModel(
        repository: TransactionRepository,
        stateRepository: FakeImportStateRepository,
        runner: ImportRunner,
        permissionGranted: Boolean,
    ) = AppViewModel(
        primaryRepository = repository,
        importStateRepository = stateRepository,
        demoRepository = null,
        importRunner = runner,
        initialPermissionGranted = permissionGranted,
        nowEpochMillis = { fixedNow },
        timeZone = { TimeZone.UTC },
    )

    private fun transaction(
        sourceId: String,
        timestamp: String = "2026-09-09T08:00:00Z",
        amountMinor: Long = 685_00,
        money: Money? = null,
    ) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = sourceId,
        sourceFingerprint = sourceId,
        transaction = ParsedTransaction(
            sourceId = sourceId,
            sourceReceivedAtEpochMillis = Instant.parse(timestamp).toEpochMilli(),
            money = money ?: Money(amountMinor, CurrencyCode.INR),
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
