package com.samsung.health.mobile.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.samsung.health.mobile.presentation.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // ▼▼▼ FIX: All Composable functions are now inside setContent ▼▼▼
            val context = LocalContext.current
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted.
                } else {
                    // Inform the user that notifications are needed for the service.
                }
            }

            fun askNotificationPermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(Unit) {
                askNotificationPermission()
            }
            // ▲▲▲ END FIX ▲▲▲

            TheApp(viewModel)
        }

        // Start the service on launch to ensure it's running
        startProcessingService()
    }

    private fun startProcessingService() {
        val intent = Intent(this, ProcessingService::class.java).apply {
            action = ProcessingService.ACTION_START
        }
        startForegroundService(intent)
    }
}

@Composable
fun TheApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    MainScreen(
        result = uiState.latestAveragedData,
        isSaving = uiState.isSaving
    )
}