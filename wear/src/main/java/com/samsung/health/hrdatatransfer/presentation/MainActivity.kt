package com.samsung.health.hrdatatransfer.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samsung.health.hrdatatransfer.presentation.theme.HealthDataTransferTheme
import com.samsung.health.hrdatatransfer.presentation.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import android.health.connect.HealthPermissions // Needed for the future-proofing logic

private const val TAG = "MainActivity"

// NOTE: BAKLAVA is currently known to be API 36 (Android 16 Developer Preview).
// The value may change in the final SDK.
private const val VERSION_CODE_BAKLAVA = 36

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HealthDataTransferTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var permissionDenied by remember { mutableStateOf(false) }

                // Modern way to request permissions in Jetpack Compose
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Check if all requested permissions were granted
                    if (permissions.values.all { it }) {
                        Log.i(TAG, "All required permissions granted.")
                        permissionDenied = false
                        // Now that permissions are granted, connect to the Health Service
                        viewModel.setUpTracking()
                    } else {
                        Log.e(TAG, "Not all permissions were granted by the user.")
                        permissionDenied = true

                        // Log which permissions were denied
                        permissions.forEach { (permission, isGranted) ->
                            if (!isGranted) {
                                Log.e(TAG, "PERMISSION DENIED: $permission")
                            }
                        }
                    }
                }

                // Final WORKING ARRAY for Android 15 (API 35)
                LaunchedEffect(Unit) {
                    val permissionList: MutableList<String> = ArrayList()

                    // NOTE: VERSION_CODE_BAKLAVA is the symbolic name for API 36 (Android 16 Developer Preview).
                    if (Build.VERSION.SDK_INT >= VERSION_CODE_BAKLAVA) {
                        // For future Android versions (API 36/BAKLAVA and up)
                        permissionList.add(HealthPermissions.READ_HEART_RATE)
                        // You may conditionally add the custom Samsung permission here if you need ECG/EDA/PPG raw data
                        // permissionList.add("com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA")
                    } else {
                        // For Android 15 (API 35) and lower, use the broad, working permission
                        permissionList.add(Manifest.permission.BODY_SENSORS)
                    }

                    // Add ACTIVITY_RECOGNITION (required on both old and new APIs):
                    permissionList.add(Manifest.permission.ACTIVITY_RECOGNITION)

                    Log.i(TAG, "Requesting permissions: $permissionList")
                    permissionLauncher.launch(permissionList.toTypedArray())
                }

                // Set up the main UI of the app
                MainScreen(
                    uiState = uiState,
                    permissionDenied = permissionDenied, // Pass the permission status to the UI
                    onToggleTracker = viewModel::toggleTracker,
                    onStartTracking = viewModel::startTracking,
                    onStopTracking = viewModel::stopTracking
                )
            }
        }
    }
}