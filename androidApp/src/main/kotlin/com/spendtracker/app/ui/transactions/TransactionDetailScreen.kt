package com.spendtracker.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.data.SourceUnavailableReason
import com.spendtracker.app.ui.PreviewData
import com.spendtracker.app.ui.SourceViewUiState
import com.spendtracker.app.ui.TransactionsUiState
import com.spendtracker.app.ui.components.StatusBanner
import com.spendtracker.app.ui.components.StatusTone
import com.spendtracker.app.ui.format.formatDate
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SpendCategory

@Composable
internal fun TransactionDetailScreen(
    state: TransactionsUiState,
    actions: TransactionActions,
    modifier: Modifier,
) {
    val row = state.selected ?: return
    // Editors start from the stored single values: parser facts until the user
    // saves a change, then the saved values.
    var category by remember(row.id, row.category) { mutableStateOf(row.category) }
    var included by remember(row.id, row.includedInSpend) { mutableStateOf(row.includedInSpend) }
    BackHandler { actions.close() }
    state.sourceView?.let { source -> SourceMessageDialog(source, actions.dismissSource) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
    ) {
        DetailTopBar(
            included = included,
            enabled = !state.isSaving,
            onBack = actions.close,
            onIncludedChanged = { included = it },
        )
        TransactionSummary(
            row = row,
            category = category,
            included = included,
        )
        DetailFacts(row)

        Text(stringResource(R.string.detail_edit_section), style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpendTrackerSpacing.CompactGroupPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Choice(
                    label = stringResource(R.string.field_category),
                    value = category,
                    options = SpendCategory.entries,
                    text = { stringResource(it.labelResource()) },
                    enabled = !state.isSaving,
                    onSelect = { category = it },
                )
            }
        }

        Button(
            onClick = { actions.save(category, included) },
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget)
                .testTag("save_transaction"),
        ) {
            Text(stringResource(if (state.isSaving) R.string.action_saving else R.string.action_save))
        }
        if (state.saveFailed) {
            StatusBanner(
                title = stringResource(R.string.edit_failed_title),
                body = stringResource(R.string.edit_failed),
                tone = StatusTone.ERROR,
            )
        }
        if (state.saveSucceeded && category == row.category && included == row.includedInSpend) {
            Text(stringResource(R.string.edit_saved), color = MaterialTheme.colorScheme.primary)
        }

        Surface(
            onClick = actions.viewSource,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("view_source_message"),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget)
                    .padding(SpendTrackerSpacing.CompactGroupPadding),
                horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap),
                ) {
                    Text(stringResource(R.string.detail_source_section), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.source_ephemeral_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.action_view), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    included: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onIncludedChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier.testTag("detail_back"),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back_transactions),
            )
        }
        Text(
            stringResource(R.string.transaction_detail_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.field_inclusion), style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = included,
            onCheckedChange = onIncludedChanged,
            enabled = enabled,
            modifier = Modifier.testTag("inclusion_toggle"),
        )
    }
}

@Composable
private fun TransactionSummary(
    row: LedgerTransaction,
    category: SpendCategory,
    included: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = category.color().copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(SpendTrackerSpacing.GroupPadding),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
        ) {
            Text(
                row.merchant ?: stringResource(R.string.transaction_unknown_merchant),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(formatMoney(row.money), style = MaterialTheme.typography.headlineMedium)
            Text(
                formatDate(row.sourceReceivedAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (row.money.currency != CurrencyCode.INR) {
                    stringResource(R.string.transaction_foreign)
                } else if (included) {
                    stringResource(R.string.filter_included)
                } else {
                    stringResource(R.string.transaction_excluded)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DetailFacts(row: LedgerTransaction) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = SpendTrackerSpacing.GroupPadding)) {
            Text(
                stringResource(R.string.detail_facts_section),
                modifier = Modifier.padding(vertical = SpendTrackerSpacing.CompactGroupPadding),
                style = MaterialTheme.typography.titleSmall,
            )
            DetailFactRow(
                label = stringResource(R.string.detail_kind_label),
                value = row.kind.name.lowercase().replace('_', ' '),
            )
            DetailFactRow(
                label = stringResource(R.string.detail_direction_label),
                value = row.direction.name.lowercase(),
            )
            row.accountHint?.let {
                DetailFactRow(
                    label = stringResource(R.string.detail_account_label),
                    value = stringResource(R.string.detail_account, it),
                )
            }
            DetailFactRow(
                label = stringResource(R.string.detail_confidence_label),
                value = stringResource(R.string.detail_confidence_value, (row.confidence * 100).toInt()),
                showDivider = false,
            )
        }
    }
}

@Composable
private fun DetailFactRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget)
                .padding(vertical = SpendTrackerSpacing.RelatedGap),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (showDivider) HorizontalDivider()
    }
}

@Composable
private fun SourceMessageDialog(source: SourceViewUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_dialog_title)) },
        text = {
            when (source) {
                SourceViewUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.source_loading))
                }

                is SourceViewUiState.Found -> Column(
                    verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                ) {
                    Text(
                        stringResource(
                            R.string.source_sender,
                            source.sender,
                            formatDate(source.receivedAtEpochMillis),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        source.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("source_message_body"),
                    )
                }

                is SourceViewUiState.Unavailable -> Text(
                    stringResource(source.reason.labelResource()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@StringRes
private fun SourceUnavailableReason.labelResource(): Int = when (this) {
    SourceUnavailableReason.NOT_ANDROID_SMS,
    SourceUnavailableReason.NO_PROVIDER_ID,
    SourceUnavailableReason.MESSAGE_NOT_FOUND -> R.string.source_unavailable_missing
    SourceUnavailableReason.PERMISSION_REVOKED -> R.string.source_unavailable_permission
    SourceUnavailableReason.LOOKUP_FAILED -> R.string.source_unavailable_error
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(
    name = "Detail large font",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.5f,
)
@Composable
private fun TransactionDetailPreview() {
    SpendTrackerTheme {
        TransactionDetailScreen(
            state = TransactionsUiState(selected = PreviewData.transaction),
            actions = TransactionActions(),
            modifier = Modifier,
        )
    }
}
