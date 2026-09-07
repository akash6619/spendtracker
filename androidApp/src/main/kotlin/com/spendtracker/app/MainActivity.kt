package com.spendtracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.spendtracker.app.di.BuildVariantDependencies
import com.spendtracker.app.ui.AppViewModel
import com.spendtracker.app.ui.SpendTrackerApp
import com.spendtracker.app.ui.theme.SpendTrackerTheme

/**
 * Android entry activity and thin platform-to-Compose boundary.
 *
 * It creates the app ViewModel from application-scoped dependencies, renders the
 * Compose hierarchy, launches the system SMS permission prompt, and refreshes
 * permission state when the activity resumes. Business logic stays outside it.
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

            SpendTrackerTheme {
                SpendTrackerApp(
                    viewModel = appViewModel,
                    onRequestSmsPermission = {
                        appViewModel.onPermissionRequestStarted()
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appViewModel.onAppResumed(
            granted = hasSmsPermission(),
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS),
        )
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
}
