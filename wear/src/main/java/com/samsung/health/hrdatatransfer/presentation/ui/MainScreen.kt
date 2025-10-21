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
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
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
    onToggleTracker: (HealthTrackerType) -> Unit,
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
                    } else {
                        SensorToggleList(
                            uiState = uiState,
                            onToggleTracker = onToggleTracker
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
private fun SensorToggleList(
    uiState: UiState,
    onToggleTracker: (HealthTrackerType) -> Unit
) {
    Text("Select Sensors", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    uiState.availableSensors.forEach { trackerType ->
        ToggleChip(
            modifier = Modifier.fillMaxWidth(),
            checked = uiState.selectedSensors.contains(trackerType),
            onCheckedChange = { onToggleTracker(trackerType) },
            label = { Text(trackerType.name.replace("_CONTINUOUS", "")) },
            toggleControl = {
                Switch(
                    checked = uiState.selectedSensors.contains(trackerType),
                    enabled = !uiState.isTracking
                )
            },
            enabled = !uiState.isTracking
        )
        Spacer(modifier = Modifier.height(4.dp))
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
            enabled = uiState.connectionState == ConnectionState.Connected && uiState.selectedSensors.isNotEmpty()
        ) {
            Text(if (uiState.isTracking) "Stop" else "Stream")
        }
    }
}
private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

@Composable
private fun DataDisplay(healthDataRecord: HealthDataRecord) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Latest Data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Time: ${timeFormatter.format(Date(healthDataRecord.timestamp))}")
        healthDataRecord.hr?.let { Text("HR: $it bpm") }
        healthDataRecord.ibi?.let { if (it.isNotEmpty()) Text("IBI: ${it.joinToString()}") }
        healthDataRecord.spo2?.let { Text("SpO2: %.1f %%".format(it)) }
        healthDataRecord.ecg?.let { Text("ECG: %.2f µV".format(it)) }
        healthDataRecord.bvp?.let { Text("BVP: %.2f".format(it)) }
        healthDataRecord.ppgGreen?.let { Text("PPG Green: $it") }
        healthDataRecord.ppgRed?.let { Text("PPG Red: $it") }
        healthDataRecord.ppgIr?.let { Text("PPG IR: $it") }
        healthDataRecord.accX?.let { Text("Acc X: $it") }
        healthDataRecord.accY?.let { Text("Acc Y: $it") }
        healthDataRecord.accZ?.let { Text("Acc Z: $it") }
        healthDataRecord.skinTemp?.let { Text("Skin Temp: %.2f °C".format(it)) }
        healthDataRecord.eda?.let { Text("EDA: %.3f µS".format(it)) }
    }
}