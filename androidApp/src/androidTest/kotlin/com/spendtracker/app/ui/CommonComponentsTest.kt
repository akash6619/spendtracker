package com.spendtracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.spendtracker.app.ui.components.ContentPane
import com.spendtracker.app.ui.components.GroupedRow
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendTrackerWidths
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies the compact foundation's width, header, and interaction contracts. */
class CommonComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun contentPaneStopsAtSharedWideScreenLimit() {
        compose.setContent {
            SpendTrackerTheme {
                ContentPane(
                    modifier = Modifier.requiredWidth(840.dp),
                    maxWidth = SpendTrackerWidths.LedgerContent,
                ) { Box(Modifier) }
            }
        }

        compose.onNodeWithTag("content_pane")
            .assertWidthIsEqualTo(SpendTrackerWidths.LedgerContent)
    }

    @Test
    fun pageHeaderMayOmitRedundantSubtitle() {
        compose.setContent {
            SpendTrackerTheme { ScreenHeader(title = "Transactions") }
        }

        compose.onNodeWithText("Transactions").assertIsDisplayed()
    }

    @Test
    fun groupedRowKeepsTouchTargetAndEmitsClick() {
        var clicks = 0
        compose.setContent {
            SpendTrackerTheme {
                GroupedRow(
                    title = "Example merchant",
                    modifier = Modifier.testTag("grouped_row"),
                    showDivider = false,
                    onClick = { clicks += 1 },
                )
            }
        }

        compose.onNodeWithTag("grouped_row")
            .assertHeightIsAtLeast(SpendTrackerSpacing.MinimumTouchTarget)
            .performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
