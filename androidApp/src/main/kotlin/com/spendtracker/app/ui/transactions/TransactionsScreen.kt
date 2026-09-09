package com.spendtracker.app.ui.transactions

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.PreviewData
import com.spendtracker.app.ui.TransactionsUiState
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.format.formatDate
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendCategoryPalette
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.Money
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
    if (showFilters) {
        FilterDialog(
            filter = state.filter,
            onApply = { actions.filter(it); showFilters = false },
            onDismiss = { showFilters = false },
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.Top,
    ) {
        item {
            LedgerHeader(
                isDemo = state.isDemo,
                filter = state.filter,
                onOpen = { showFilters = true },
                onClear = { actions.filter(TransactionFilter()) },
            )
        }
        item { Spacer(Modifier.height(SpendTrackerSpacing.RelatedGap)) }
        if (state.isLoading) item { CircularProgressIndicator() }
        if (state.loadFailed) {
            item {
                Column {
                    Text(stringResource(R.string.ledger_load_failed))
                    TextButton(onClick = actions.retry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }

        if (state.transactions.isEmpty() && !state.isLoading && !state.loadFailed) {
            item {
                InfoCard(
                    title = stringResource(
                        if (state.filter == TransactionFilter()) {
                            R.string.transactions_empty_title
                        } else {
                            R.string.filter_empty_title
                        },
                    ),
                    body = stringResource(
                        if (state.filter == TransactionFilter()) {
                            R.string.transactions_empty_body
                        } else {
                            R.string.filter_empty_body
                        },
                    ),
                )
            }
        } else {
            itemsIndexed(
                items = state.transactions,
                key = { _, transaction -> transaction.id },
            ) { index, transaction ->
                TransactionRow(
                    transaction = transaction,
                    isFirst = index == 0,
                    isLast = index == state.transactions.lastIndex,
                    showDivider = index < state.transactions.lastIndex,
                    onClick = { actions.open(transaction.id) },
                )
            }
        }
    }
}

@Composable
private fun LedgerHeader(
    isDemo: Boolean,
    filter: TransactionFilter,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val activeCount = filter.activeDimensionCount()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.transactions_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (isDemo) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.demo_badge),
                    modifier = Modifier.padding(
                        horizontal = SpendTrackerSpacing.RelatedGap,
                        vertical = 2.dp,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (activeCount > 0) {
            TextButton(onClick = onClear, modifier = Modifier.testTag("clear_filters")) {
                Text(stringResource(R.string.clear_filters))
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.testTag("open_filters")) {
            Text(
                if (activeCount == 0) {
                    stringResource(R.string.ledger_filters)
                } else {
                    stringResource(R.string.active_filters_count, activeCount)
                },
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: LedgerTransaction,
    isFirst: Boolean,
    isLast: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val amount = formatMoney(transaction.money)
    val category = stringResource(transaction.category.labelResource())
    val date = formatDate(transaction.sourceReceivedAtEpochMillis)
    val merchant = transaction.merchant ?: stringResource(R.string.transaction_unknown_merchant)
    val status = when {
        transaction.money.currency != CurrencyCode.INR -> stringResource(R.string.transaction_foreign)
        !transaction.includedInSpend -> stringResource(R.string.transaction_excluded)
        else -> null
    }
    val spokenDescription = listOfNotNull(merchant, amount, category, date, status).joinToString(", ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = spokenDescription }
                .testTag("transaction_row_${transaction.id}"),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = when {
                isFirst && isLast -> MaterialTheme.shapes.medium
                isFirst -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                isLast -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                else -> RectangleShape
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(
                        start = SpendTrackerSpacing.SectionGap,
                        end = SpendTrackerSpacing.CompactGroupPadding,
                        top = SpendTrackerSpacing.RelatedGap,
                        bottom = SpendTrackerSpacing.RelatedGap,
                    ),
                horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = SpendTrackerSpacing.TightGap)
                        .size(width = 4.dp, height = 40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(SpendCategoryPalette.color(transaction.category)),
                )
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val stackAmount = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.3f
                    if (stackAmount) {
                        Column(verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap)) {
                            TransactionIdentity(merchant, category, date)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(amount, style = MaterialTheme.typography.titleSmall)
                                status?.let { TransactionStatus(it) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TransactionIdentity(merchant, category, date)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(amount, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                status?.let {
                                    Spacer(Modifier.height(SpendTrackerSpacing.TightGap))
                                    TransactionStatus(it)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = SpendTrackerSpacing.SectionGap))
        }
    }
}

@Composable
private fun TransactionIdentity(
    merchant: String,
    category: String,
    date: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap)) {
        Text(
            merchant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.transaction_meta, category, date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransactionStatus(status: String) {
    Text(
        status,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        maxLines = 1,
    )
}

private fun TransactionFilter.activeDimensionCount(): Int = listOf(
    fromInclusive != null || toExclusive != null,
    category != null,
    included != null,
    needsReview != null,
    currency != null,
    foreignOnly,
).count { it }

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Preview(
    name = "Ledger large font",
    showBackground = true,
    widthDp = 360,
    heightDp = 720,
    fontScale = 1.5f,
)
@Preview(
    name = "Ledger dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TransactionsContentPreview() {
    SpendTrackerTheme {
        TransactionsScreen(
            TransactionsUiState(
                transactions = listOf(
                    PreviewData.transaction,
                    PreviewData.transaction.copy(
                        id = "preview-excluded",
                        merchant = "City ATM",
                        includedInSpend = false,
                    ),
                    PreviewData.transaction.copy(
                        id = "preview-foreign",
                        merchant = "Example Airways",
                        money = Money(125_00, CurrencyCode.USD),
                    ),
                    PreviewData.transaction.copy(id = "preview-four", merchant = "Metro Pass"),
                    PreviewData.transaction.copy(id = "preview-five", merchant = "Corner Grocer"),
                ),
                isDemo = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionsEmptyPreview() {
    SpendTrackerTheme { TransactionsScreen(TransactionsUiState()) }
}
