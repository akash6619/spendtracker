package com.spendtracker.app.ui

import com.spendtracker.app.data.InMemoryTransactionRepository
import com.spendtracker.app.data.MessageScanResult
import com.spendtracker.app.data.MessageScanner
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.ParsedTransaction
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind
import com.spendtracker.core.model.TransactionCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun successfulScanMovesFromOnboardingToDashboard() = runTest {
        val transaction = transaction(sourceId = "synthetic-scan")
        val viewModel = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(),
            demoRepository = null,
            messageScanner = MessageScanner {
                MessageScanResult(
                    scannedMessages = 12,
                    transactions = listOf(transaction),
                )
            },
            initialPermissionGranted = false,
        )
        advanceUntilIdle()

        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertEquals(PermissionUiState.REQUIRED, viewModel.uiState.value.permission)

        viewModel.onPermissionChanged(true)
        viewModel.onScanMessages()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AppStage.MAIN, state.stage)
        assertEquals(TopLevelDestination.DASHBOARD, state.selectedDestination)
        assertEquals(12, state.scanSummary?.scannedMessages)
        assertEquals(1, state.scanSummary?.recognizedTransactions)
        assertEquals(685_00, state.dashboard.inrSpendMinor)
        assertEquals(listOf(transaction.transaction), state.transactions.transactions.map { it.transaction })
        assertFalse(state.isScanning)
    }

    @Test
    fun failedScanKeepsOnboardingAndShowsRecoverableError() = runTest {
        val viewModel = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(),
            demoRepository = null,
            messageScanner = MessageScanner { error("Synthetic scanner failure") },
            initialPermissionGranted = true,
        )

        viewModel.onScanMessages()
        advanceUntilIdle()

        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertEquals(AppError.SCAN_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    fun demoRepositorySupportsNavigationWithoutPermission() = runTest {
        val demoTransaction = transaction(sourceId = "synthetic-demo")
        val viewModel = AppViewModel(
            primaryRepository = InMemoryTransactionRepository(),
            demoRepository = InMemoryTransactionRepository(listOf(demoTransaction)),
            messageScanner = MessageScanner { MessageScanResult(0, emptyList()) },
            initialPermissionGranted = false,
        )
        advanceUntilIdle()

        viewModel.onUseDemoData()
        advanceUntilIdle()
        viewModel.onDestinationSelected(TopLevelDestination.TRANSACTIONS)

        val demoState = viewModel.uiState.value
        assertEquals(AppStage.MAIN, demoState.stage)
        assertEquals(TopLevelDestination.TRANSACTIONS, demoState.selectedDestination)
        assertTrue(demoState.usingDemoData)
        assertTrue(demoState.dashboard.isDemo)
        assertEquals(listOf(demoTransaction.transaction), demoState.transactions.transactions.map { it.transaction })

        viewModel.onLeaveDemoData()
        advanceUntilIdle()

        assertEquals(AppStage.ONBOARDING, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.usingDemoData)
    }

    private fun transaction(sourceId: String) = TransactionCandidate(
        sourceType = SourceType.ANDROID_SMS,
        sourceProviderId = sourceId,
        sourceFingerprint = sourceId,
        transaction = ParsedTransaction(
            sourceId = sourceId,
            sourceReceivedAtEpochMillis = 1_788_457_600_000,
            money = Money(amountMinor = 685_00, currency = CurrencyCode.INR),
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
