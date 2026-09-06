package com.spendtracker.app.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.core.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Intents emitted by the ledger and editor to the application ViewModel.
 * Default callbacks keep previews independent of Android services. UI never
 * reads storage directly; null edit values explicitly reset detected defaults.
 */
data class TransactionActions(
    val filter: (TransactionFilter) -> Unit = {},
    val open: (String) -> Unit = {},
    val close: () -> Unit = {},
    val save: (SpendCategory?, Boolean?) -> Unit = { _, _ -> },
    val retry: () -> Unit = {},
)

@Composable
internal fun <T> Choice(
    label: String,
    value: T,
    options: List<T>,
    text: @Composable (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text("$label: ${text(value)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
internal fun FilterDialog(filter: TransactionFilter, onApply: (TransactionFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(filter) }
    val zone = ZoneId.systemDefault()
    var from by remember { mutableStateOf(filter.fromInclusive?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }.orEmpty()) }
    var to by remember { mutableStateOf(filter.toExclusive?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().minusDays(1).toString() }.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ledger_filters)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it; invalid = false }, label = { Text(stringResource(R.string.filter_from)) }, singleLine = true)
                OutlinedTextField(to, { to = it; invalid = false }, label = { Text(stringResource(R.string.filter_to)) }, singleLine = true)
                Choice(stringResource(R.string.field_category), draft.category, listOf(null) + SpendCategory.entries,
                    { it?.let { category -> stringResource(category.labelResource()) } ?: stringResource(R.string.filter_all) }) { draft = draft.copy(category = it) }
                Choice(stringResource(R.string.field_inclusion), draft.included, listOf(null, true, false),
                    { stringResource(when(it) { true -> R.string.filter_included; false -> R.string.transaction_excluded; null -> R.string.filter_all }) }) { draft = draft.copy(included = it) }
                Choice(stringResource(R.string.field_review), draft.needsReview, listOf(null, true, false),
                    { stringResource(when(it) { true -> R.string.review_needed; false -> R.string.review_clear; null -> R.string.filter_all }) }) { draft = draft.copy(needsReview = it) }
                Choice(stringResource(R.string.field_currency), draft.currency, listOf(null) + CurrencyCode.entries,
                    { it?.name ?: stringResource(R.string.filter_all) }) { draft = draft.copy(currency = it) }
                if (invalid) Text(stringResource(R.string.filter_invalid_date), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val start = from.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    val end = to.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    require(start == null || end == null || !end.isBefore(start))
                    onApply(draft.copy(
                        fromInclusive = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                        toExclusive = end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    ))
                } catch (_: Exception) { invalid = true }
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
