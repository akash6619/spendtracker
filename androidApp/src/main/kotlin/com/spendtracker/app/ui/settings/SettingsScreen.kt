package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.AppError
import com.spendtracker.app.ui.AppUiState
import com.spendtracker.app.ui.PermissionUiState
import com.spendtracker.app.ui.SettingsUiState
import com.spendtracker.app.ui.UiTestTags
import com.spendtracker.app.ui.components.DemoBanner
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.format.formatDate
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendTrackerSpacing

/**
 * Intents the settings/privacy screen emits to the ViewModel.
 * Default callbacks keep previews independent of Android services and storage.
 */
data class SettingsActions(
    val onRequestPermission: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onLeaveDemoData: () -> Unit = {},
    val onRequestDeleteAll: () -> Unit = {},
    val onCancelDeleteAll: () -> Unit = {},
    val onConfirmDeleteAll: () -> Unit = {},
    val onDeleteAllNoticeShown: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    state: AppUiState,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
        )
        if (state.usingDemoData) DemoBanner()

        if (state.error == AppError.SCAN_FAILED) {
            InfoCard(
                title = stringResource(R.string.scan_failed_title),
                body = stringResource(R.string.scan_failed_body),
                isError = true,
            )
        }

        SmsAccessCard(state, actions)
        StatusCard(state)
        InfoCard(
            title = stringResource(R.string.what_counts_title),
            body = stringResource(R.string.what_counts_body),
        )
        InfoCard(
            title = stringResource(R.string.local_only_title),
            body = stringResource(R.string.local_only_body),
        )
        PrivacyCard(state)
        DeleteDataCard(state, actions)

        if (state.usingDemoData) {
            InfoCard(
                title = stringResource(R.string.demo_mode_title),
                body = stringResource(R.string.demo_mode_body),
            ) {
                OutlinedButton(
                    onClick = actions.onLeaveDemoData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.LEAVE_DEMO),
                ) {
                    Text(stringResource(R.string.action_leave_demo))
                }
            }
        }
    }

    if (state.settings.showDeleteConfirm) {
        DeleteConfirmDialog(state, actions)
    }
}

@Composable
private fun SmsAccessCard(state: AppUiState, actions: SettingsActions) {
    InfoCard(
        title = stringResource(R.string.sms_access_title),
        body = stringResource(
            if (state.permission == PermissionUiState.GRANTED) {
                R.string.sms_access_granted
            } else {
                R.string.sms_access_required
            },
        ),
    ) {
        Text(
            stringResource(R.string.import_window),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.isScanning -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scanning_locally))
                }
                OutlinedButton(
                    onClick = actions.onCancelImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CANCEL_IMPORT),
                ) {
                    Text(stringResource(R.string.action_cancel_import))
                }
            }

            state.permission == PermissionUiState.GRANTED -> {
                Text(
                    stringResource(R.string.sms_auto_pickup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = actions.onOpenAppSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manage_sms"),
                ) {
                    Text(stringResource(R.string.action_manage_sms))
                }
            }

            state.permission == PermissionUiState.NOT_REQUESTED ||
                state.permission == PermissionUiState.DENIED -> Button(
                onClick = actions.onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.ALLOW_SMS),
            ) {
                Text(stringResource(R.string.action_allow_sms))
            }

            else -> Button(
                onClick = actions.onOpenAppSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.OPEN_SETTINGS),
            ) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
    }
}

@Composable
private fun StatusCard(state: AppUiState) {
    val settings = state.settings
    if (state.usingDemoData) return
    InfoCard(
        title = stringResource(R.string.status_title),
        body = stringResource(
            if (settings.lastScanEpochMillis == null) R.string.status_never_scanned else R.string.status_last_scan,
            settings.lastScanEpochMillis?.let(::formatDate).orEmpty(),
        ),
    ) {
        Text(stringResource(R.string.status_parser_version, settings.parserVersion))
        Text(
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.status_stored_count,
                settings.storedTransactionCount,
                settings.storedTransactionCount,
            ),
        )
    }
}

@Composable
private fun PrivacyCard(state: AppUiState) {
    InfoCard(
        title = stringResource(R.string.privacy_policy_title),
        body = stringResource(R.string.privacy_policy_body),
    ) {
        Text(
            stringResource(R.string.foreign_explanation_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteDataCard(state: AppUiState, actions: SettingsActions) {
    if (state.usingDemoData) return
    InfoCard(
        title = stringResource(R.string.data_lifecycle_title),
        body = stringResource(R.string.data_lifecycle_body),
    ) {
        if (state.settings.deleteAllFailed) {
            Text(stringResource(R.string.delete_failed), color = MaterialTheme.colorScheme.error)
        }
        if (state.settings.deleteAllSucceeded) {
            Text(stringResource(R.string.delete_succeeded), color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = actions.onRequestDeleteAll,
            enabled = !state.settings.isDeletingAll && !state.isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("delete_all"),
        ) {
            if (state.settings.isDeletingAll) {
                Text(stringResource(R.string.deleting))
            } else {
                Text(stringResource(R.string.action_delete_all))
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(state: AppUiState, actions: SettingsActions) {
    AlertDialog(
        onDismissRequest = actions.onCancelDeleteAll,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = actions.onConfirmDeleteAll, enabled = !state.settings.isDeletingAll) {
                Text(stringResource(R.string.action_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onCancelDeleteAll) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SpendTrackerTheme {
        SettingsScreen(
            state = AppUiState(
                permission = PermissionUiState.GRANTED,
                usingDemoData = true,
                demoAvailable = true,
                settings = SettingsUiState(parserVersion = 3, storedTransactionCount = 5),
            ),
        )
    }
}
