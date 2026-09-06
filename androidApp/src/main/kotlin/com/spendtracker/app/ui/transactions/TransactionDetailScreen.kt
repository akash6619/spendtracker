package com.spendtracker.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.data.SourceUnavailableReason
import com.spendtracker.app.ui.SourceViewUiState
import com.spendtracker.app.ui.TransactionsUiState
import com.spendtracker.app.ui.format.*
import com.spendtracker.core.model.*

@Composable
internal fun TransactionDetailScreen(state: TransactionsUiState, actions: TransactionActions, modifier: Modifier) {
    val row = state.selected ?: return
    // Editors start from the stored single values: parser facts until the user
    // saves a change, then the saved values.
    var category by remember(row.id, row.category) { mutableStateOf(row.category) }
    var included by remember(row.id, row.includedInSpend) { mutableStateOf(row.includedInSpend) }
    BackHandler { actions.close() }
    state.sourceView?.let { source ->
        SourceMessageDialog(source, actions.dismissSource)
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = actions.close, enabled = !state.isSaving) { Text(stringResource(R.string.action_back_transactions)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_inclusion), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = included,
                    onCheckedChange = { included = it },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("inclusion_toggle"),
                )
            }
        }
        Text(row.merchant ?: stringResource(R.string.transaction_unknown_merchant), style = MaterialTheme.typography.headlineSmall)
        Text(formatMoney(row.money), style = MaterialTheme.typography.headlineMedium)
        Text(formatDate(row.sourceReceivedAtEpochMillis))
        Text(stringResource(R.string.detail_kind, row.kind.name.lowercase().replace('_', ' '), row.direction.name.lowercase()))
        row.accountHint?.let { Text(stringResource(R.string.detail_account, it)) }
        Text(inclusionExplanation(row))
        Text(stringResource(R.string.detail_confidence, (row.confidence * 100).toInt()))
        HorizontalDivider()
        Choice(stringResource(R.string.field_category), category, SpendCategory.entries,
            { stringResource(it.labelResource()) }, !state.isSaving) { category = it }
        Button(onClick = { actions.save(category, included) }, enabled = !state.isSaving) {
            Text(stringResource(if (state.isSaving) R.string.action_saving else R.string.action_save))
        }
        if (state.saveFailed) Text(stringResource(R.string.edit_failed), color = MaterialTheme.colorScheme.error)
        if (state.saveSucceeded && category == row.category && included == row.includedInSpend) Text(stringResource(R.string.edit_saved))
        OutlinedButton(
            onClick = actions.viewSource,
            modifier = Modifier.testTag("view_source_message"),
        ) {
            Text(stringResource(R.string.action_view_source))
        }
        Text(stringResource(R.string.source_ephemeral_note), style = MaterialTheme.typography.bodySmall)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.source_loading))
                }

                is SourceViewUiState.Found -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
internal fun inclusionExplanation(row: LedgerTransaction): String = stringResource(when {
    row.money.currency != CurrencyCode.INR -> R.string.foreign_explanation
    row.includedInSpend -> R.string.included_by_default
    row.reviewReasons.any { it == TransactionReviewReason.CONFLICTING_AMOUNTS || it == TransactionReviewReason.CONFLICTING_DIRECTIONS } -> R.string.excluded_conflict
    else -> R.string.excluded_by_kind
})