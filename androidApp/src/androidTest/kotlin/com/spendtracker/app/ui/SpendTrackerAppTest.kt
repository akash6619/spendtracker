package com.spendtracker.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpendTrackerAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topLevelDestinationsAreReachable() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    AppUiState(
                        stage = AppStage.MAIN,
                        usingDemoData = true,
                        dashboard = DashboardUiState(isDemo = true),
                        transactions = TransactionsUiState(isDemo = true),
                    ),
                )
            }
            SpendTrackerTheme {
                SpendTrackerAppContent(
                    state = state,
                    onRequestSmsPermission = {},
                    onScanMessages = {},
                    onUseDemoData = {},
                    onLeaveDemoData = {},
                    onDestinationSelected = { destination ->
                        state = state.copy(selectedDestination = destination)
                    },
                )
            }
        }

        composeRule.onNodeWithText("No recognized spend yet").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.NAV_TRANSACTIONS).performClick()
        composeRule.onNodeWithText("Filters").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.NAV_SETTINGS).performClick()
        composeRule.onNodeWithText("Local-only MVP").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.NAV_DASHBOARD).performClick()
        composeRule.onNodeWithText("No recognized spend yet").assertIsDisplayed()
    }

    @Test
    fun onboardingShowsErrorAndDebugDemoAction() {
        composeRule.setContent {
            SpendTrackerTheme {
                SpendTrackerAppContent(
                    state = AppUiState(
                        stage = AppStage.ONBOARDING,
                        permission = PermissionUiState.GRANTED,
                        error = AppError.SCAN_FAILED,
                        demoAvailable = true,
                    ),
                    onRequestSmsPermission = {},
                    onScanMessages = {},
                    onUseDemoData = {},
                    onLeaveDemoData = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Scan wasn’t completed").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SCAN_MESSAGES).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.EXPLORE_DEMO).assertIsDisplayed()
    }

    @Test
    fun deniedAndBlockedPermissionExposeCorrectRecoveryAction() {
        val permission = mutableStateOf(PermissionUiState.DENIED)
        composeRule.setContent {
            SpendTrackerTheme {
                SpendTrackerAppContent(
                    state = AppUiState(stage = AppStage.ONBOARDING, permission = permission.value),
                    onRequestSmsPermission = { permission.value = PermissionUiState.PERMANENTLY_DENIED },
                    onScanMessages = {},
                    onUseDemoData = {},
                    onLeaveDemoData = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ALLOW_SMS).assertIsDisplayed()
        composeRule.runOnIdle { permission.value = PermissionUiState.PERMANENTLY_DENIED }
        composeRule.onNodeWithTag(UiTestTags.OPEN_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun runningImportShowsSafeProgressAndCancelAction() {
        composeRule.setContent {
            SpendTrackerTheme {
                SpendTrackerAppContent(
                    state = AppUiState(
                        stage = AppStage.ONBOARDING,
                        permission = PermissionUiState.GRANTED,
                        isScanning = true,
                        importProgress = com.spendtracker.core.importing.ImportProgress(scannedMessages = 42),
                    ),
                    onRequestSmsPermission = {},
                    onScanMessages = {},
                    onUseDemoData = {},
                    onLeaveDemoData = {},
                    onDestinationSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("42 messages inspected").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CANCEL_IMPORT).assertIsDisplayed()
    }
}
