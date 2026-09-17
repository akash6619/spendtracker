package com.spendtracker.app.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.spendtracker.app.ui.theme.SpendCategoryPalette
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SpendCategory
import com.spendtracker.core.model.TransactionDirection
import com.spendtracker.core.model.TransactionKind

@Composable
internal fun TransactionDetailScreen(
    state: TransactionsUiState,
    actions: TransactionActions,
    modifier: Modifier,
) {
    val row = state.selected ?: return
    // Editors start from the stored single values and submit each committed
    // control change immediately, without a separate save step.
    var merchant by remember(row.id, row.merchant) { mutableStateOf(row.merchant.orEmpty()) }
    var kind by remember(row.id, row.kind) { mutableStateOf(row.kind) }
    var direction by remember(row.id, row.direction) { mutableStateOf(row.direction) }
    var category by remember(row.id, row.category) { mutableStateOf(row.category) }
    var included by remember(row.id, row.includedInSpend) { mutableStateOf(row.includedInSpend) }
    var isEditingDetails by remember(row.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun submitDetails() {
        isEditingDetails = false
        actions.update(merchant, kind, direction, category, included)
        focusManager.clearFocus()
    }
    BackHandler {
        if (isEditingDetails) {
            merchant = row.merchant.orEmpty()
            kind = row.kind
            direction = row.direction
            isEditingDetails = false
            focusManager.clearFocus()
        } else {
            actions.close()
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
    ) {
        DetailTopBar(
            included = included,
            enabled = true,
            onBack = actions.close,
            onIncludedChanged = {
                included = it
                actions.update(merchant, kind, direction, category, it)
            },
        )
        TransactionSummary(
            row = row,
            merchant = merchant,
            category = category,
            included = included,
            isEditingDetails = isEditingDetails,
            onMerchantChanged = { merchant = it },
            onEditDetails = { isEditingDetails = true },
            onConfirmDetails = ::submitDetails,
        )
        DetailFacts(
            row = row,
            kind = kind,
            direction = direction,
            isEditing = isEditingDetails,
            onKindChanged = { kind = it },
            onDirectionChanged = { direction = it },
        )

        Text(stringResource(R.string.detail_edit_section), style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpendTrackerSpacing.CompactGroupPadding),
                verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            ) {
                Choice(
                    label = stringResource(R.string.field_category),
                    value = category,
                    options = SpendCategory.entries,
                    text = { stringResource(it.labelResource()) },
                    onSelect = {
                        category = it
                        actions.update(merchant, kind, direction, it, included)
                    },
                )
            }
        }
        if (state.isSaving) Text(stringResource(R.string.action_saving))
        if (state.saveFailed) {
            StatusBanner(
                title = stringResource(R.string.edit_failed_title),
                body = stringResource(R.string.edit_failed),
                tone = StatusTone.ERROR,
            )
        }
        if (state.saveSucceeded && merchant.trim().takeIf(String::isNotEmpty) == row.merchant && kind == row.kind && direction == row.direction && category == row.category && included == row.includedInSpend) {
            Text(stringResource(R.string.edit_saved), color = MaterialTheme.colorScheme.primary)
        }

        SourceMessageSection(state.sourceView)
    }
}

@Composable
private fun SourceMessageSection(source: SourceViewUiState?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpendTrackerSpacing.CompactGroupPadding),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
        ) {
            Text(stringResource(R.string.detail_source_section), style = MaterialTheme.typography.titleSmall)
            when (source) {
                null,
                SourceViewUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.source_loading), style = MaterialTheme.typography.bodyMedium)
                }

                is SourceViewUiState.Found -> {
                    Text(
                        stringResource(
                            R.string.source_sender,
                            source.sender,
                            formatDate(source.receivedAtEpochMillis),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        source.body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("source_message_body"),
                    )
                }

                is SourceViewUiState.Unavailable -> Text(
                    stringResource(source.reason.labelResource()),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
        Row(
            modifier = Modifier.padding(end = SpendTrackerSpacing.TightGap),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.field_inclusion), style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = included,
                onCheckedChange = onIncludedChanged,
                enabled = enabled,
                modifier = Modifier.testTag("inclusion_toggle"),
            )
        }
    }
}

@Composable
private fun TransactionSummary(
    row: LedgerTransaction,
    merchant: String,
    category: SpendCategory,
    included: Boolean,
    isEditingDetails: Boolean,
    onMerchantChanged: (String) -> Unit,
    onEditDetails: () -> Unit,
    onConfirmDetails: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SpendCategoryPalette.color(category).copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(SpendTrackerSpacing.GroupPadding),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
        ) {
            if (isEditingDetails) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = onMerchantChanged,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("merchant_field"),
                        label = { Text(stringResource(R.string.field_merchant)) },
                        placeholder = { Text(stringResource(R.string.transaction_unknown_merchant)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirmDetails() }),
                    )
                    IconButton(
                        onClick = onConfirmDetails,
                        modifier = Modifier.testTag("confirm_merchant_edit"),
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.action_confirm_merchant),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.merchant ?: stringResource(R.string.transaction_unknown_merchant),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("edit_merchant")
                            .clickable(onClick = onEditDetails),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.action_edit_merchant),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
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
private fun DetailFacts(
    row: LedgerTransaction,
    kind: TransactionKind,
    direction: TransactionDirection,
    isEditing: Boolean,
    onKindChanged: (TransactionKind) -> Unit,
    onDirectionChanged: (TransactionDirection) -> Unit,
) {
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
            if (isEditing) {
                Choice(
                    label = stringResource(R.string.detail_kind_label),
                    value = kind,
                    options = TransactionKind.entries,
                    text = { it.name.lowercase().replace('_', ' ') },
                    onSelect = onKindChanged,
                )
                Choice(
                    label = stringResource(R.string.detail_direction_label),
                    value = direction,
                    options = TransactionDirection.entries,
                    text = { it.name.lowercase() },
                    onSelect = onDirectionChanged,
                )
            } else {
                DetailFactRow(
                    label = stringResource(R.string.detail_kind_label),
                    value = row.kind.name.lowercase().replace('_', ' '),
                )
                DetailFactRow(
                    label = stringResource(R.string.detail_direction_label),
                    value = row.direction.name.lowercase(),
                )
            }
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
