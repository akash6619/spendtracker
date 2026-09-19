package com.spendtracker.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendtracker.app.data.FinancialNotificationListenerService
import com.spendtracker.app.data.LiveTransactionNotifier
import com.spendtracker.app.di.BuildVariantDependencies
import com.spendtracker.app.ui.AppViewModel
import com.spendtracker.app.ui.SpendTrackerApp
import com.spendtracker.app.ui.theme.SpendTrackerTheme

/**
 * Android entry activity and thin platform-to-Compose boundary.
 *
 * It creates the app ViewModel from application-scoped dependencies, renders the
 * Compose hierarchy, launches explicit SMS/notification permission flows, routes
 * transaction alert intents, and refreshes platform access state on resume.
 * Business logic stays outside it.
 */
class MainActivity : ComponentActivity() {
    private val dependencies: SpendTrackerApplication
        get() = application as SpendTrackerApplication

    private val appViewModel by viewModels<AppViewModel> {
        AppViewModel.Factory(
            primaryRepository = dependencies.repository,
            importStateRepository = dependencies.importStateRepository,
            demoRepository = BuildVariantDependencies.createDemoRepository(),
            importRunner = dependencies.importCoordinator,
            initialPermissionGranted = hasSmsPermission(),
            sourceMessageLookup = dependencies.sourceLookup,
            deleteAllLocalData = { dependencies.deleteAllLocalData() },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                appViewModel.onPermissionResult(
                    granted = granted,
                    shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS),
                )
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { refreshNotificationCapabilities() }

            SpendTrackerTheme {
                SpendTrackerApp(
                    viewModel = appViewModel,
                    onRequestSmsPermission = {
                        appViewModel.onPermissionRequestStarted()
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                    onOpenAppSettings = ::openAppSettings,
                    onOpenNotificationAccess = ::openNotificationAccessSettings,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings()
                        }
                    },
                )
            }
        }
        openTransactionFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appViewModel.onAppResumed(
            granted = hasSmsPermission(),
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS),
        )
        refreshNotificationCapabilities()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTransactionFromIntent(intent)
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openNotificationAccessSettings() {
        val component = ComponentName(this, FinancialNotificationListenerService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        runCatching { startActivity(intent) }
            .getOrElse { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun refreshNotificationCapabilities() {
        val listenerGranted = packageName in
            NotificationManagerCompat.getEnabledListenerPackages(this)
        val notificationsGranted = dependencies.liveTransactionNotifier.canPostAlerts()
        appViewModel.onNotificationCapabilitiesChanged(listenerGranted, notificationsGranted)
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun openTransactionFromIntent(intent: Intent?) {
        if (intent?.action != LiveTransactionNotifier.ACTION_OPEN_TRANSACTION) return
        intent.getStringExtra(LiveTransactionNotifier.EXTRA_TRANSACTION_ID)
            ?.let(appViewModel::onOpenTransactionFromNotification)
        intent.removeExtra(LiveTransactionNotifier.EXTRA_TRANSACTION_ID)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
