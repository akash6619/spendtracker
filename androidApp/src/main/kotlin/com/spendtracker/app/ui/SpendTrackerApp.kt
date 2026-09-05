package com.spendtracker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.R
import com.spendtracker.app.ui.dashboard.DashboardScreen
import com.spendtracker.app.ui.onboarding.OnboardingScreen
import com.spendtracker.app.ui.settings.SettingsScreen
import com.spendtracker.app.ui.transactions.TransactionsScreen

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
    const val EXPLORE_DEMO = "explore_demo"
    const val LEAVE_DEMO = "leave_demo"
}

@Composable
fun SpendTrackerApp(
    viewModel: AppViewModel,
    onRequestSmsPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpendTrackerAppContent(
        state = state,
        onRequestSmsPermission = onRequestSmsPermission,
        onScanMessages = viewModel::onScanMessages,
        onUseDemoData = viewModel::onUseDemoData,
        onLeaveDemoData = viewModel::onLeaveDemoData,
        onDestinationSelected = viewModel::onDestinationSelected,
    )
}

@Composable
fun SpendTrackerAppContent(
    state: AppUiState,
    onRequestSmsPermission: () -> Unit,
    onScanMessages: () -> Unit,
    onUseDemoData: () -> Unit,
    onLeaveDemoData: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    when (state.stage) {
        AppStage.ONBOARDING -> OnboardingScreen(
            state = state,
            onRequestPermission = onRequestSmsPermission,
            onScanMessages = onScanMessages,
            onUseDemoData = onUseDemoData,
        )

        AppStage.MAIN -> MainAppScaffold(
            state = state,
            onRequestSmsPermission = onRequestSmsPermission,
            onScanMessages = onScanMessages,
            onLeaveDemoData = onLeaveDemoData,
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Composable
private fun MainAppScaffold(
    state: AppUiState,
    onRequestSmsPermission: () -> Unit,
    onScanMessages: () -> Unit,
    onLeaveDemoData: () -> Unit,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
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
                        modifier = Modifier.testTag(navigationItem.testTag),
                    )
                }
            }
        },
    ) { padding ->
        when (state.selectedDestination) {
            TopLevelDestination.DASHBOARD -> DashboardScreen(
                state = state.dashboard,
                modifier = Modifier.padding(padding),
            )

            TopLevelDestination.TRANSACTIONS -> TransactionsScreen(
                state = state.transactions,
                modifier = Modifier.padding(padding),
            )

            TopLevelDestination.SETTINGS -> SettingsScreen(
                state = state,
                onRequestPermission = onRequestSmsPermission,
                onScanMessages = onScanMessages,
                onLeaveDemoData = onLeaveDemoData,
                modifier = Modifier.padding(padding),
            )
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
