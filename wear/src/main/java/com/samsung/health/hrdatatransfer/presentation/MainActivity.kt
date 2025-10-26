package com.samsung.health.hrdatatransfer.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samsung.health.hrdatatransfer.presentation.theme.HealthDataTransferTheme
import com.samsung.health.hrdatatransfer.presentation.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import android.health.connect.HealthPermissions

private const val TAG = "MainActivity"
private const val VERSION_CODE_BAKLAVA = 36 // This seems to be a custom constant, not standard

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthDataTransferTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var permissionDenied by remember { mutableStateOf(false) }

                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.all { it }) {
                        Log.i(TAG, "All required permissions granted.")
                        permissionDenied = false

                        // ▼▼▼ THIS IS THE FIX ▼▼▼
                        // Start the service immediately after permissions are granted.
                        // This will trigger its onCreate() and connectToHealthService().
                        startService(context, HealthTrackingService.ACTION_PREPARE)
                        // ▲▲▲ END FIX ▲▲▲

                    } else {
                        Log.e(TAG, "Not all permissions were granted by the user.")
                        permissionDenied = true
                        permissions.forEach { (permission, isGranted) ->
                            if (!isGranted) {
                                Log.e(TAG, "PERMISSION DENIED: $permission")
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val permissionList: MutableList<String> = ArrayList()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (Build.VERSION.SDK_INT >= VERSION_CODE_BAKLAVA) { // Using your custom constant
                        permissionList.add(HealthPermissions.READ_HEART_RATE)
                    } else {
                        permissionList.add(Manifest.permission.BODY_SENSORS)
                    }
                    permissionList.add(Manifest.permission.ACTIVITY_RECOGNITION)

                    Log.i(TAG, "Requesting permissions: $permissionList")
                    permissionLauncher.launch(permissionList.toTypedArray())
                }

                MainScreen(
                    uiState = uiState,
                    permissionDenied = permissionDenied,
                    onStartTracking = {
                        startService(context, HealthTrackingService.ACTION_START)
                    },
                    onStopTracking = {
                        startService(context, HealthTrackingService.ACTION_STOP)
                    }
                )
            }
        }
    }

    private fun startService(context: Context, action: String) {
        val intent = Intent(context, HealthTrackingService::class.java).apply {
            this.action = action
        }
        context.startForegroundService(intent)
    }
}