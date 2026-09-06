package com.spendtracker.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.spendtracker.app.ui.transactions.TransactionActions
import com.spendtracker.app.ui.transactions.TransactionsScreen
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.model.*
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
        compose.onNodeWithText("Spend inclusion: All").performScrollTo().performClick()
        compose.onNodeWithText("Excluded from spend").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.onNodeWithText("No matching transactions").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        compose.onNodeWithText(PreviewData.transaction.transaction.merchant!!).assertIsDisplayed()
    }

    @Test
    fun detailSavesCategoryAndInclusionAndResetsDefaults() {
        compose.setContent {
            var row by remember { mutableStateOf(PreviewData.transaction) }
            var selected by remember { mutableStateOf(false) }
            SpendTrackerTheme {
                TransactionsScreen(
                    TransactionsUiState(transactions = listOf(row), selected = row.takeIf { selected }),
                    actions = TransactionActions(
                        open = { selected = true }, close = { selected = false },
                        save = { category, included -> row = row.copy(userCategory = category, userIncludedInSpend = included) },
                    ),
                )
            }
        }
        compose.onNodeWithText(PreviewData.transaction.transaction.merchant!!).performClick()
        compose.onNodeWithText("Category: Use detected default").performScrollTo().performClick()
        compose.onNodeWithText("Travel").performScrollTo().performClick()
        compose.onNodeWithText("Spend inclusion: Use detected default").performScrollTo().performClick()
        compose.onNodeWithText("Excluded from spend").performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.onNodeWithText("Excluded from spend by your override.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reset to detected defaults").performScrollTo().performClick()
        compose.onNodeWithText("Included in spend as a detected purchase or fee debit.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to transactions").performScrollTo().performClick()
        compose.onNodeWithText("Filters").assertIsDisplayed()
    }
}
