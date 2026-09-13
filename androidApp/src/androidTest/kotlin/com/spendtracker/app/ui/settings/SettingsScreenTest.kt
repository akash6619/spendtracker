package com.spendtracker.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendtracker.app.ui.AppStage
import com.spendtracker.app.ui.AppUiState
import com.spendtracker.app.ui.PermissionUiState
import com.spendtracker.app.ui.SettingsUiState
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun statusShowsLastScanParserVersionAndStoredCount() {
        compose.setContent {
            SpendTrackerTheme {
                SettingsScreen(
                    AppUiState(
                        stage = AppStage.MAIN,
                        permission = PermissionUiState.GRANTED,
                        settings = SettingsUiState(
                            lastScanEpochMillis = 1_789_000_000_000L,
                            parserVersion = 3,
                            storedTransactionCount = 12,
                        ),
                    ),
                )
            }
        }
        compose.onNodeWithText("SMS access").assertIsDisplayed()
        compose.onNodeWithText("Last scan").assertIsDisplayed()
        compose.onNodeWithText("Stored transactions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parser version").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What counts as spend").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete all SpendTracker data").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteFlowShowsConfirmationAndEmitsConfirmCancel() {
        val showConfirm = mutableStateOf(false)
        val requested = mutableListOf<String>()
        var deleting = false
        compose.setContent {
            SpendTrackerTheme {
                SettingsScreen(
                    AppUiState(
                        stage = AppStage.MAIN,
                        permission = PermissionUiState.GRANTED,
                        settings = SettingsUiState(showDeleteConfirm = showConfirm.value, isDeletingAll = deleting),
                    ),
                    actions = SettingsActions(
                        onRequestDeleteAll = { requested += "request"; showConfirm.value = true },
                        onCancelDeleteAll = { requested += "cancel"; showConfirm.value = false },
                        onConfirmDeleteAll = { requested += "confirm"; deleting = true },
                    ),
                )
            }
        }
        compose.onNodeWithTag("delete_all").performScrollTo().performClick()
        compose.onNodeWithText("Delete all SpendTracker data?").assertIsDisplayed()
        compose.onNodeWithText("Delete everything").performClick()
        assertEquals(listOf("request", "confirm"), requested)

        showConfirm.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Delete everything").assertIsDisplayed()
        // Dismiss via outside area not needed; exercise cancel path separately below.
    }

    @Test
    fun deleteCancelPathDismissesDialog() {
        val showConfirm = mutableStateOf(true)
        val cancelled = mutableStateOf(false)
        compose.setContent {
            SpendTrackerTheme {
                SettingsScreen(
                    AppUiState(
                        stage = AppStage.MAIN,
                        settings = SettingsUiState(showDeleteConfirm = showConfirm.value),
                    ),
                    actions = SettingsActions(
                        onCancelDeleteAll = { showConfirm.value = false; cancelled.value = true },
                    ),
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled.value)
    }
}
