package com.spendtracker.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.spendtracker.app.R
import com.spendtracker.app.ui.AppError
import com.spendtracker.app.ui.AppStage
import com.spendtracker.app.ui.AppUiState
import com.spendtracker.app.ui.PermissionUiState
import com.spendtracker.app.ui.UiTestTags
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendTrackerSpacing

@Composable
fun OnboardingScreen(
    state: AppUiState,
    onRequestPermission: () -> Unit,
    onScanMessages: () -> Unit,
    onCancelImport: () -> Unit,
    onUseDemoData: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SpendTrackerSpacing.PageMargin),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SpendTrackerSpacing.RelatedGap),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap)) {
                Text(
                    stringResource(R.string.onboarding_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.onboarding_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.error == AppError.SCAN_FAILED) {
            InfoCard(
                title = stringResource(R.string.scan_failed_title),
                body = stringResource(R.string.scan_failed_body),
                isError = true,
            )
        }

        when (state.permission) {
            PermissionUiState.NOT_REQUESTED -> InfoCard(
                title = stringResource(R.string.permission_required_title),
                body = stringResource(R.string.permission_required_body),
            ) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.ALLOW_SMS),
                ) {
                    Text(stringResource(R.string.action_allow_sms))
                }
            }

            PermissionUiState.DENIED -> InfoCard(
                title = stringResource(R.string.permission_denied_title),
                body = stringResource(R.string.permission_denied_body),
                isError = true,
            ) {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.ALLOW_SMS),
                ) {
                    Text(stringResource(R.string.action_try_permission_again))
                }
            }

            PermissionUiState.PERMANENTLY_DENIED,
            PermissionUiState.REVOKED -> InfoCard(
                title = stringResource(
                    if (state.permission == PermissionUiState.REVOKED) {
                        R.string.permission_revoked_title
                    } else {
                        R.string.permission_blocked_title
                    },
                ),
                body = stringResource(
                    if (state.permission == PermissionUiState.REVOKED) {
                        R.string.permission_revoked_body
                    } else {
                        R.string.permission_blocked_body
                    },
                ),
                isError = true,
            ) {
                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.OPEN_SETTINGS),
                ) {
                    Text(stringResource(R.string.action_open_settings))
                }
            }

            PermissionUiState.GRANTED -> InfoCard(
                title = stringResource(R.string.permission_granted_title),
                body = stringResource(R.string.permission_granted_body),
            ) {
                if (state.error == AppError.SCAN_FAILED && !state.isScanning) {
                    Button(
                        onClick = onScanMessages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.SCAN_MESSAGES),
                    ) {
                        Text(stringResource(R.string.action_try_again))
                    }
                }
            }
        }

        if (state.isScanning) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(SpendTrackerSpacing.CompactGroupPadding),
                    horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(SpendTrackerSpacing.TightGap))
                    Text(
                        pluralStringResource(
                            R.plurals.scan_progress_count,
                            state.importProgress.scannedMessages,
                            state.importProgress.scannedMessages,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onCancelImport,
                        modifier = Modifier.testTag(UiTestTags.CANCEL_IMPORT),
                    ) { Text(stringResource(R.string.action_cancel_import)) }
                }
            }
        }

        if (state.demoAvailable) {
            OutlinedButton(
                onClick = onUseDemoData,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.EXPLORE_DEMO),
            ) {
                Text(stringResource(R.string.action_explore_demo))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingPermissionPreview() {
    SpendTrackerTheme {
        OnboardingScreen(
            state = AppUiState(stage = AppStage.ONBOARDING, demoAvailable = true),
            onRequestPermission = {},
            onScanMessages = {},
            onCancelImport = {},
            onUseDemoData = {},
            onOpenAppSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingLoadingPreview() {
    SpendTrackerTheme {
        OnboardingScreen(
            state = AppUiState(
                stage = AppStage.ONBOARDING,
                permission = PermissionUiState.GRANTED,
                isScanning = true,
            ),
            onRequestPermission = {},
            onScanMessages = {},
            onCancelImport = {},
            onUseDemoData = {},
            onOpenAppSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingErrorPreview() {
    SpendTrackerTheme {
        OnboardingScreen(
            state = AppUiState(
                stage = AppStage.ONBOARDING,
                permission = PermissionUiState.GRANTED,
                error = AppError.SCAN_FAILED,
            ),
            onRequestPermission = {},
            onScanMessages = {},
            onCancelImport = {},
            onUseDemoData = {},
            onOpenAppSettings = {},
        )
    }
}
