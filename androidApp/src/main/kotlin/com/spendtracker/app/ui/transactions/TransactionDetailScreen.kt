package com.spendtracker.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.TransactionsUiState
import com.spendtracker.app.ui.format.*
import com.spendtracker.core.model.*

@Composable
internal fun TransactionDetailScreen(state: TransactionsUiState, actions: TransactionActions, modifier: Modifier) {
    val row = state.selected ?: return
    val parsed = row.transaction
    var category by remember(row.id, row.userCategory) { mutableStateOf(row.userCategory) }
    var included by remember(row.id, row.userIncludedInSpend) { mutableStateOf(row.userIncludedInSpend) }
    BackHandler { actions.close() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = actions.close, enabled = !state.isSaving) { Text(stringResource(R.string.action_back_transactions)) }
        Text(parsed.merchant ?: stringResource(R.string.transaction_unknown_merchant), style = MaterialTheme.typography.headlineSmall)
        Text(formatMoney(parsed.money), style = MaterialTheme.typography.headlineMedium)
        Text(formatDate(parsed.sourceReceivedAtEpochMillis))
        Text(stringResource(R.string.detail_kind, parsed.kind.name.lowercase().replace('_', ' '), parsed.direction.name.lowercase()))
        Text(stringResource(R.string.detail_detected_category, stringResource(parsed.category.labelResource())))
        parsed.accountHint?.let { Text(stringResource(R.string.detail_account, it)) }
        Text(inclusionExplanation(row))
        Text(stringResource(R.string.detail_confidence, (parsed.confidence * 100).toInt()))
        Text(stringResource(if (row.needsReview) R.string.review_needed else R.string.review_clear))
        parsed.reviewReasons.forEach { reason -> Text(stringResource(reason.labelResource())) }
        Text(stringResource(R.string.detail_review_note), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Choice(stringResource(R.string.field_category), category, listOf(null) + SpendCategory.entries,
            { it?.let { value -> stringResource(value.labelResource()) } ?: stringResource(R.string.use_detected) }, !state.isSaving) { category = it }
        Choice(stringResource(R.string.field_inclusion), included, listOf(null, true, false),
            { stringResource(when(it) { true -> R.string.filter_included; false -> R.string.transaction_excluded; null -> R.string.use_detected }) }, !state.isSaving) { included = it }
        Button(onClick = { actions.save(category, included) }, enabled = !state.isSaving) {
            Text(stringResource(if (state.isSaving) R.string.action_saving else R.string.action_save))
        }
        OutlinedButton(onClick = { category = null; included = null; actions.save(null, null) }, enabled = !state.isSaving) {
            Text(stringResource(R.string.action_reset_overrides))
        }
        if (state.saveFailed) Text(stringResource(R.string.edit_failed), color = MaterialTheme.colorScheme.error)
        if (state.saveSucceeded && category == row.userCategory && included == row.userIncludedInSpend) Text(stringResource(R.string.edit_saved))
        Text(stringResource(R.string.source_not_enabled), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun inclusionExplanation(row: LedgerTransaction): String = stringResource(when {
    row.transaction.money.currency != CurrencyCode.INR -> R.string.foreign_explanation
    row.userIncludedInSpend == true -> R.string.included_by_user
    row.userIncludedInSpend == false -> R.string.excluded_by_user
    row.isIncludedInSpend -> R.string.included_by_default
    row.transaction.reviewReasons.any { it == TransactionReviewReason.CONFLICTING_AMOUNTS || it == TransactionReviewReason.CONFLICTING_DIRECTIONS } -> R.string.excluded_conflict
    else -> R.string.excluded_by_kind
})

private fun TransactionReviewReason.labelResource(): Int = when(this) {
    TransactionReviewReason.UNKNOWN_CATEGORY -> R.string.reason_category
    TransactionReviewReason.UNKNOWN_KIND -> R.string.reason_kind
    TransactionReviewReason.MISSING_MERCHANT -> R.string.reason_merchant
    TransactionReviewReason.CONFLICTING_AMOUNTS -> R.string.reason_amount
    TransactionReviewReason.CONFLICTING_DIRECTIONS -> R.string.reason_direction
}
