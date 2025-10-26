package com.samsung.health.hrdatatransfer.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.data.HealthDataRecord
import com.samsung.health.hrdatatransfer.presentation.ConnectionState
import com.samsung.health.hrdatatransfer.presentation.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    uiState: UiState,
    permissionDenied: Boolean,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.connectionState == ConnectionState.Connecting) {
            CircularProgressIndicator()
        } else if (permissionDenied) {
            Text(
                text = "Permissions denied. Please grant all permissions in App Settings to continue.",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    if (uiState.connectionState == ConnectionState.Failed) {
                        Text(
                            text = "Connection to Health Service failed. Please restart the app.",
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ControlButtons(
                        uiState = uiState,
                        onStartTracking = onStartTracking,
                        onStopTracking = onStopTracking
                    )
                }
                if (uiState.isTracking && uiState.latestData != null) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        DataDisplay(healthDataRecord = uiState.latestData)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButtons(
    uiState: UiState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = if (uiState.isTracking) onStopTracking else onStartTracking,
            // Enable button only when connected and not tracking, or when tracking
            enabled = uiState.connectionState == ConnectionState.Connected
        ) {
            Text(if (uiState.isTracking) "Stop" else "Stream")
        }
    }
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun DataDisplay(healthDataRecord: HealthDataRecord) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Latest Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Time: ${timeFormatter.format(Date(healthDataRecord.timestamp))}")

        // --- Display "---" if data is null or 0 (for 0.0f) ---
        Text("HR: ${healthDataRecord.hr?.takeIf { it > 0 } ?: "---"} bpm")
        Text("IBI: ${healthDataRecord.ibi?.joinToString() ?: "---"}")

        // --- SPO2 REMOVED ---

        Text("ECG: ${healthDataRecord.ecg?.let { "%.2f µV".format(it) } ?: "---"}")
        Text("PPG Green: ${healthDataRecord.ppgGreen?.takeIf { it > 0 } ?: "---"}")
        Text("PPG Red: ${healthDataRecord.ppgRed?.takeIf { it > 0 } ?: "---"}")
        Text("PPG IR: ${healthDataRecord.ppgIr?.takeIf { it > 0 } ?: "---"}")
        Text("Acc X: ${healthDataRecord.accX ?: "---"}")
        Text("Acc Y: ${healthDataRecord.accY ?: "---"}")
        Text("Acc Z: ${healthDataRecord.accZ ?: "---"}")
        Text("Skin Temp: ${healthDataRecord.skinTemp?.takeIf { it > 0.0f }?.let { "%.2f °C".format(it) } ?: "---"}")
        Text("EDA: ${healthDataRecord.eda?.let { "%.3f µS".format(it) } ?: "---"}")
    }
}

