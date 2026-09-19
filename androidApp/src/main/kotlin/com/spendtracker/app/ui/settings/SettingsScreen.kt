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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.spendtracker.app.ui.components.GroupedRow
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.components.SectionHeading
import com.spendtracker.app.ui.components.StatusBanner
import com.spendtracker.app.ui.components.StatusTone
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
    val onOpenNotificationAccess: () -> Unit = {},
    val onRequestNotificationPermission: () -> Unit = {},
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

        SectionHeading(stringResource(R.string.settings_access_section))
        SmsAccessCard(state, actions)
        NotificationDetectionCard(state, actions)
        StatusCard(state)
        SectionHeading(stringResource(R.string.settings_rules_section))
        InfoCard(
            title = stringResource(R.string.what_counts_title),
            body = stringResource(R.string.what_counts_body),
        )
        InfoCard(
            title = stringResource(R.string.local_only_title),
            body = stringResource(R.string.local_only_body),
        )
        PrivacyCard(state)
        SectionHeading(stringResource(R.string.settings_data_section))
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
private fun NotificationDetectionCard(state: AppUiState, actions: SettingsActions) {
    val detectionEnabled = state.permission == PermissionUiState.GRANTED &&
        state.settings.notificationAccessGranted
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            GroupedRow(
                title = stringResource(R.string.notification_detection_title),
                trailing = stringResource(
                    if (detectionEnabled) {
                        R.string.notification_detection_on
                    } else {
                        R.string.notification_detection_off
                    },
                ),
                metadata = stringResource(R.string.notification_detection_summary),
                showDivider = true,
            )
            Column(
                modifier = Modifier.padding(SpendTrackerSpacing.CompactGroupPadding),
                verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            ) {
                Text(
                    stringResource(R.string.notification_detection_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = actions.onOpenNotificationAccess,
                    modifier = Modifier.fillMaxWidth().testTag("notification_access"),
                ) {
                    Text(stringResource(
                        if (state.settings.notificationAccessGranted) {
                            R.string.action_manage_notification_access
                        } else {
                            R.string.action_enable_notification_access
                        },
                    ))
                }
                if (!state.settings.transactionNotificationsGranted) {
                    Button(
                        onClick = actions.onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth().testTag("transaction_notifications"),
                    ) {
                        Text(stringResource(R.string.action_enable_transaction_notifications))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsAccessCard(state: AppUiState, actions: SettingsActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
      Column {
        GroupedRow(
            title = stringResource(R.string.sms_access_title),
            trailing = stringResource(if (state.permission == PermissionUiState.GRANTED) R.string.sms_access_granted else R.string.sms_access_required),
            metadata = stringResource(R.string.import_window),
            showDivider = true,
        )
        Column(
            modifier = Modifier.padding(SpendTrackerSpacing.CompactGroupPadding),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
        ) {
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
                    modifier = Modifier.testTag(UiTestTags.CANCEL_IMPORT),
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
                    modifier = Modifier.testTag("manage_sms"),
                ) {
                    Text(stringResource(R.string.action_manage_sms))
                }
            }

            state.permission == PermissionUiState.NOT_REQUESTED ||
                state.permission == PermissionUiState.DENIED -> Button(
                onClick = actions.onRequestPermission,
                modifier = Modifier
                    .testTag(UiTestTags.ALLOW_SMS),
            ) {
                Text(stringResource(R.string.action_allow_sms))
            }

            else -> Button(
                onClick = actions.onOpenAppSettings,
                modifier = Modifier
                    .testTag(UiTestTags.OPEN_SETTINGS),
            ) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
        }
      }
    }
}

@Composable
private fun StatusCard(state: AppUiState) {
    val settings = state.settings
    if (state.usingDemoData) return
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column {
            GroupedRow(
                title = stringResource(R.string.status_last_scan_label),
                trailing = if (settings.lastScanEpochMillis == null) stringResource(R.string.status_never_scanned) else formatDate(settings.lastScanEpochMillis),
            )
            GroupedRow(
                title = stringResource(R.string.status_parser_label),
                trailing = settings.parserVersion.toString(),
            )
            GroupedRow(
                title = stringResource(R.string.status_stored_label),
                trailing = settings.storedTransactionCount.toString(),
                showDivider = false,
            )
        }
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
            StatusBanner(stringResource(R.string.delete_failed_title), stringResource(R.string.delete_failed), tone = StatusTone.ERROR)
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
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
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
