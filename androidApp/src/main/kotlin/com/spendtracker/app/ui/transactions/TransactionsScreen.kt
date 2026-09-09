package com.spendtracker.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.PreviewData
import com.spendtracker.app.ui.TransactionsUiState
import com.spendtracker.app.ui.components.DemoBanner
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.format.formatDate
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.TransactionFilter

@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    modifier: Modifier = Modifier,
    actions: TransactionActions = TransactionActions(),
) {
    if (state.selected != null) {
        TransactionDetailScreen(state, actions, modifier)
        return
    }
    var showFilters by remember { mutableStateOf(false) }
    if (showFilters) FilterDialog(state.filter, { actions.filter(it); showFilters = false }, { showFilters = false })
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.transactions_title),
            )
        }
        if (state.isDemo) item { DemoBanner() }

        item {
            Column {
                TextButton(onClick = { showFilters = true }) { Text(stringResource(R.string.ledger_filters)) }
                if (state.filter != TransactionFilter()) {
                    Text(stringResource(R.string.filters_active))
                    TextButton(onClick = { actions.filter(TransactionFilter()) }) { Text(stringResource(R.string.clear_filters)) }
                }
            }
        }
        if (state.isLoading) item { CircularProgressIndicator() }
        if (state.loadFailed) item {
            Column {
                Text(stringResource(R.string.ledger_load_failed))
                TextButton(onClick = actions.retry) { Text(stringResource(R.string.action_retry)) }
            }
        }

        if (state.transactions.isEmpty() && !state.isLoading && !state.loadFailed) {
            item {
                InfoCard(
                    title = stringResource(if (state.filter == TransactionFilter()) R.string.transactions_empty_title else R.string.filter_empty_title),
                    body = stringResource(if (state.filter == TransactionFilter()) R.string.transactions_empty_body else R.string.filter_empty_body),
                )
            }
        } else {
            items(
                items = state.transactions,
                key = { transaction ->
                    transaction.id
                },
            ) { transaction ->
                TransactionRow(transaction) { actions.open(transaction.id) }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: LedgerTransaction, onClick: () -> Unit) {
    val amount = formatMoney(transaction.money)
    val category = stringResource(transaction.category.labelResource())
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    transaction.merchant
                        ?: stringResource(R.string.transaction_unknown_merchant),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        R.string.transaction_meta,
                        category,
                        formatDate(transaction.sourceReceivedAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    transaction.money.currency != CurrencyCode.INR -> Text(
                        stringResource(R.string.transaction_foreign),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    !transaction.includedInSpend -> Text(
                        stringResource(R.string.transaction_excluded),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(amount, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionsContentPreview() {
    SpendTrackerTheme {
        TransactionsScreen(
            TransactionsUiState(
                transactions = listOf(PreviewData.transaction),
                isDemo = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionsEmptyPreview() {
    SpendTrackerTheme {
        TransactionsScreen(TransactionsUiState())
    }
}
