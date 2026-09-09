package com.spendtracker.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.spendtracker.app.ui.transactions.TransactionActions
import com.spendtracker.app.ui.transactions.TransactionsScreen
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the ledger's filter dialog, editor intents, and accessible empty state with synthetic rows. */
class TransactionsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun combinedFiltersProduceEmptyResultsAndCanBeCleared() {
        compose.setContent {
            var filter by remember { mutableStateOf(TransactionFilter()) }
            SpendTrackerTheme {
                TransactionsScreen(
                    TransactionsUiState(transactions = listOf(PreviewData.transaction).filteredBy(filter), filter = filter),
                    actions = TransactionActions(filter = { filter = it }),
                )
            }
        }
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("Currency: All").performScrollTo().performClick()
        compose.onNodeWithText("JPY").performClick()
        compose.onNodeWithText("Spend: All").performScrollTo().performClick()
        compose.onNodeWithText("Excluded from spend").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("Filters · 2 active").assertIsDisplayed()
        compose.onNodeWithText("No matching transactions").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        compose.onNodeWithText(PreviewData.transaction.merchant!!).assertIsDisplayed()
    }

    @Test
    fun detailSavesCategoryAndInclusionStartingFromDetectedValues() {
        compose.setContent {
            var row by remember { mutableStateOf(PreviewData.transaction) }
            var selected by remember { mutableStateOf(false) }
            SpendTrackerTheme {
                TransactionsScreen(
                    TransactionsUiState(transactions = listOf(row), selected = row.takeIf { selected }),
                    actions = TransactionActions(
                        open = { selected = true }, close = { selected = false },
                        save = { category, included -> row = row.copy(category = category, includedInSpend = included) },
                    ),
                )
            }
        }
        compose.onNodeWithText(PreviewData.transaction.merchant!!).performClick()
        // Category editor shows the stored value; inclusion is a toggle at top right.
        compose.onNodeWithText("Category: Food & dining").assertIsDisplayed()
        compose.onNodeWithTag("inclusion_toggle").assertIsDisplayed()
        compose.onNodeWithText("Category: Food & dining").performScrollTo().performClick()
        compose.onNodeWithText("Travel").performScrollTo().performClick()
        compose.onNodeWithTag("inclusion_toggle").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("Excluded from spend").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail_back").performClick()
        compose.onNodeWithText("Filters").assertIsDisplayed()
    }

    @Test
    fun compactLedgerShowsFourRowsAndAnnouncesExcludedAndForeignStatus() {
        val included = PreviewData.transaction
        val rows = listOf(
            included,
            included.copy(id = "excluded", merchant = "City ATM", includedInSpend = false),
            included.copy(
                id = "foreign",
                merchant = "Example Airways",
                money = Money(125_00, CurrencyCode.USD),
            ),
            included.copy(id = "transport", merchant = "Metro Pass"),
            included.copy(id = "grocer", merchant = "Corner Grocer"),
        )
        compose.setContent {
            SpendTrackerTheme { TransactionsScreen(TransactionsUiState(transactions = rows)) }
        }

        rows.take(4).forEach { row ->
            compose.onNodeWithTag("transaction_row_${row.id}").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(
            "City ATM, ₹685.00, Food & dining, 4 Sep 2026, Excluded from spend",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Example Airways, US$125.00, Food & dining, 4 Sep 2026, Not included in INR total",
        ).assertIsDisplayed()
    }

    @Test
    fun sourceViewShowsFoundBodyEphemerallyAndDismisses() {
        var dismissed = 0
        val source = mutableStateOf<SourceViewUiState?>(
            SourceViewUiState.Found("SYNTHETIC SENDER", "SYNTHETIC MESSAGE BODY", 1_788_457_600_000),
        )
        compose.setContent {
            SpendTrackerTheme {
                TransactionsScreen(
                    TransactionsUiState(
                        transactions = listOf(PreviewData.transaction),
                        selected = PreviewData.transaction,
                        sourceView = source.value,
                    ),
                    actions = TransactionActions(dismissSource = { source.value = null; dismissed += 1 }),
                )
            }
        }
        compose.onNodeWithTag("source_message_body").assertIsDisplayed()
        compose.onNodeWithText("SYNTHETIC MESSAGE BODY").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("SYNTHETIC MESSAGE BODY").assertDoesNotExist()
        assertEquals(1, dismissed)
    }

    @Test
    fun sourceViewExplainsUnavailableMessageAndViewSourceButtonEmitsIntent() {
        var viewed = 0
        val source = mutableStateOf<SourceViewUiState?>(null)
        compose.setContent {
            SpendTrackerTheme {
                TransactionsScreen(
                    TransactionsUiState(
                        transactions = listOf(PreviewData.transaction),
                        selected = PreviewData.transaction,
                        sourceView = source.value,
                    ),
                    actions = TransactionActions(
                        viewSource = { viewed += 1 },
                        dismissSource = { source.value = null },
                    ),
                )
            }
        }
        compose.onNodeWithTag("view_source_message").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("view_source_message").performClick()
        assertEquals(1, viewed)

        compose.runOnIdle {
            source.value = SourceViewUiState.Unavailable(
                com.spendtracker.app.data.SourceUnavailableReason.MESSAGE_NOT_FOUND,
            )
        }
        compose.onNodeWithText("The original message is no longer available on this device. It may have been deleted or replaced.").assertIsDisplayed()
    }
}
