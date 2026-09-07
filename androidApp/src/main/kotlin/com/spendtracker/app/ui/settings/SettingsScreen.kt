package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.AppError
import com.spendtracker.app.ui.AppUiState
import com.spendtracker.app.ui.PermissionUiState
import com.spendtracker.app.ui.UiTestTags
import com.spendtracker.app.ui.components.DemoBanner
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.theme.SpendTrackerTheme

@Composable
fun SettingsScreen(
    state: AppUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCancelImport: () -> Unit,
    onLeaveDemoData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        if (state.usingDemoData) DemoBanner()

        if (state.error == AppError.SCAN_FAILED) {
            InfoCard(
                title = stringResource(R.string.scan_failed_title),
                body = stringResource(R.string.scan_failed_body),
                isError = true,
            )
        }

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
            state.scanSummary?.let { summary ->
                Text(
                    pluralStringResource(
                        R.plurals.scan_inspected_count,
                        summary.scannedMessages,
                        summary.scannedMessages,
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.scan_recognized_count,
                        summary.recognizedTransactions,
                        summary.recognizedTransactions,
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.scan_saved_count,
                        summary.savedTransactions,
                        summary.savedTransactions,
                    ),
                )
                Text(
                    pluralStringResource(
                        R.plurals.scan_rejected_count,
                        summary.rejectedMessages,
                        summary.rejectedMessages,
                    ),
                )
            }
            if (state.isScanning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        pluralStringResource(
                            R.plurals.scan_progress_count,
                            state.importProgress.scannedMessages,
                            state.importProgress.scannedMessages,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onCancelImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.CANCEL_IMPORT),
                ) {
                    Text(stringResource(R.string.action_cancel_import))
                }
            } else if (state.permission == PermissionUiState.GRANTED) {
                Text(
                    stringResource(R.string.sms_auto_pickup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.permission == PermissionUiState.NOT_REQUESTED ||
                state.permission == PermissionUiState.DENIED
            ) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.ALLOW_SMS),
                ) {
                    Text(stringResource(R.string.action_allow_sms))
                }
            } else {
                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.OPEN_SETTINGS),
                ) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }
        }

        InfoCard(
            title = stringResource(R.string.local_only_title),
            body = stringResource(R.string.local_only_body),
        )

        if (state.usingDemoData) {
            InfoCard(
                title = stringResource(R.string.demo_mode_title),
                body = stringResource(R.string.demo_mode_body),
            ) {
                OutlinedButton(
                    onClick = onLeaveDemoData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.LEAVE_DEMO),
                ) {
                    Text(stringResource(R.string.action_leave_demo))
                }
            }
        }
    }
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
            ),
            onRequestPermission = {},
            onOpenAppSettings = {},
            onCancelImport = {},
            onLeaveDemoData = {},
        )
    }
}
