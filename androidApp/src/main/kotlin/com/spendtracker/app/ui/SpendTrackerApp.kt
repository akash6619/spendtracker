package com.spendtracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.R
import com.spendtracker.app.ui.components.ContentPane
import com.spendtracker.app.ui.dashboard.DashboardActions
import com.spendtracker.app.ui.dashboard.DashboardScreen
import com.spendtracker.app.ui.onboarding.OnboardingScreen
import com.spendtracker.app.ui.settings.SettingsActions
import com.spendtracker.app.ui.settings.SettingsScreen
import com.spendtracker.app.ui.transactions.TransactionsScreen
import com.spendtracker.app.ui.transactions.TransactionActions
import com.spendtracker.app.ui.theme.SpendTrackerWidths

/**
 * Stable semantic identifiers attached to important Compose controls.
 * Connected tests use these values instead of visible copy, allowing text to
 * evolve without making navigation and permission tests brittle.
 */
object UiTestTags {
    const val NAV_DASHBOARD = "nav_dashboard"
    const val NAV_TRANSACTIONS = "nav_transactions"
    const val NAV_SETTINGS = "nav_settings"
    const val ALLOW_SMS = "allow_sms"
    const val SCAN_MESSAGES = "scan_messages"
    const val CANCEL_IMPORT = "cancel_import"
    const val EXPLORE_DEMO = "explore_demo"
    const val LEAVE_DEMO = "leave_demo"
    const val OPEN_SETTINGS = "open_settings"
}

@Composable
fun SpendTrackerApp(
    viewModel: AppViewModel,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpendTrackerAppContent(
        state = state,
        onRequestSmsPermission = onRequestSmsPermission,
        onOpenAppSettings = onOpenAppSettings,
        onScanMessages = viewModel::onScanMessages,
        onCancelImport = viewModel::onCancelImport,
        onUseDemoData = viewModel::onUseDemoData,
        onLeaveDemoData = viewModel::onLeaveDemoData,
        onDestinationSelected = viewModel::onDestinationSelected,
        transactionActions = TransactionActions(
            filter = viewModel::onTransactionFilterChanged,
            open = viewModel::onTransactionSelected,
            close = viewModel::onTransactionClosed,
            update = viewModel::onUpdateTransaction,
            retry = viewModel::onRetryTransactions,
        ),
        dashboardActions = DashboardActions(
            onPeriodSelected = viewModel::onDashboardPeriodSelected,
            onPreviousPeriod = viewModel::onDashboardPreviousPeriod,
            onNextPeriod = viewModel::onDashboardNextPeriod,
            onCategorySelected = viewModel::onDashboardCategorySelected,
            onExcludedSelected = viewModel::onDashboardExcludedSelected,
            onForeignSelected = viewModel::onDashboardForeignSelected,
        ),
        settingsActions = SettingsActions(
            onRequestPermission = onRequestSmsPermission,
            onOpenAppSettings = onOpenAppSettings,
            onCancelImport = viewModel::onCancelImport,
            onLeaveDemoData = viewModel::onLeaveDemoData,
            onRequestDeleteAll = viewModel::onRequestDeleteAll,
            onCancelDeleteAll = viewModel::onCancelDeleteAll,
            onConfirmDeleteAll = viewModel::onConfirmDeleteAll,
            onDeleteAllNoticeShown = viewModel::onDeleteAllNoticeShown,
        ),
    )
}

@Composable
fun SpendTrackerAppContent(
    state: AppUiState,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onScanMessages: () -> Unit,
    onCancelImport: () -> Unit = {},
    onUseDemoData: () -> Unit,
    onLeaveDemoData: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    transactionActions: TransactionActions = TransactionActions(),
    dashboardActions: DashboardActions = DashboardActions(),
    settingsActions: SettingsActions = SettingsActions(),
) {
    when (state.stage) {
        AppStage.INITIALIZING -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        AppStage.ONBOARDING -> ContentPane(
            modifier = Modifier.fillMaxSize(),
            maxWidth = SpendTrackerWidths.FocusedContent,
        ) {
            OnboardingScreen(
                state = state,
                onRequestPermission = onRequestSmsPermission,
                onScanMessages = onScanMessages,
                onCancelImport = onCancelImport,
                onUseDemoData = onUseDemoData,
                onOpenAppSettings = onOpenAppSettings,
            )
        }

        AppStage.MAIN -> MainAppScaffold(
            state = state,
            onDestinationSelected = onDestinationSelected,
            transactionActions = transactionActions,
            dashboardActions = dashboardActions,
            settingsActions = settingsActions,
        )
    }
}

@Composable
private fun MainAppScaffold(
    state: AppUiState,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    transactionActions: TransactionActions,
    dashboardActions: DashboardActions,
    settingsActions: SettingsActions,
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val navigationItem = destination.navigationItem()
                    val label = stringResource(navigationItem.labelResource)
                    NavigationBarItem(
                        selected = state.selectedDestination == destination,
                        onClick = { onDestinationSelected(destination) },
                        icon = {
                            Icon(
                                imageVector = navigationItem.icon,
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                        alwaysShowLabel = true,
                        modifier = Modifier.testTag(navigationItem.testTag),
                    )
                }
            }
        },
    ) { padding ->
        ContentPane(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = if (
                state.selectedDestination == TopLevelDestination.TRANSACTIONS &&
                state.transactions.selected != null
            ) {
                SpendTrackerWidths.FocusedContent
            } else {
                SpendTrackerWidths.LedgerContent
            },
        ) {
            when (state.selectedDestination) {
                TopLevelDestination.DASHBOARD -> DashboardScreen(
                    state = state.dashboard,
                    actions = dashboardActions,
                )

                TopLevelDestination.TRANSACTIONS -> TransactionsScreen(
                    state = state.transactions,
                    actions = transactionActions,
                )

                TopLevelDestination.SETTINGS -> SettingsScreen(
                    state = state,
                    actions = settingsActions,
                )
            }
        }
    }
}

/**
 * Internal presentation metadata for one bottom-navigation destination.
 * It keeps the destination enum independent from Android resources and Compose
 * icon types while supplying the scaffold with everything needed to render it.
 */
private data class NavigationItem(
    val icon: ImageVector,
    val labelResource: Int,
    val testTag: String,
)

private fun TopLevelDestination.navigationItem(): NavigationItem = when (this) {
    TopLevelDestination.DASHBOARD -> NavigationItem(
        icon = Icons.Outlined.Dashboard,
        labelResource = R.string.nav_dashboard,
        testTag = UiTestTags.NAV_DASHBOARD,
    )

    TopLevelDestination.TRANSACTIONS -> NavigationItem(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        labelResource = R.string.nav_transactions,
        testTag = UiTestTags.NAV_TRANSACTIONS,
    )

    TopLevelDestination.SETTINGS -> NavigationItem(
        icon = Icons.Outlined.Settings,
        labelResource = R.string.nav_settings,
        testTag = UiTestTags.NAV_SETTINGS,
    )
}
