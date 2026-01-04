package com.samsung.health.hrdatatransfer.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
// CRITICAL FIX: The correct import for standard icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (permissionDenied) {
            PermissionErrorScreen()
        } else if (uiState.connectionState == ConnectionState.Connecting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connecting...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberScalingLazyListState()

            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    ConnectionStatusChip(uiState.connectionState)
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    PrimaryActionButton(
                        isTracking = uiState.isTracking,
                        isConnected = uiState.connectionState == ConnectionState.Connected,
                        onStart = onStartTracking,
                        onStop = onStopTracking
                    )
                }

                if (uiState.isTracking && uiState.latestData != null) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        TelemetryCard(batch = uiState.latestData)
                    }
                }

                if (uiState.connectionState == ConnectionState.Failed) {
                    item {
                        Text(
                            text = "Error: ${uiState.connectionException?.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun ConnectionStatusChip(state: ConnectionState) {
    // FIX: Removed ambiguous destructuring. We define variables explicitly.
    val icon = when (state) {
        ConnectionState.Connected -> Icons.Filled.Bluetooth
        ConnectionState.Failed -> Icons.Filled.Warning
        else -> Icons.Filled.BluetoothDisabled
    }

    val text = when (state) {
        ConnectionState.Connected -> "Phone Connected"
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Failed -> "Connection Failed"
        else -> "Unknown"
    }

    val color = when (state) {
        ConnectionState.Connected -> Color(0xFF96be25) // Samsung Green
        ConnectionState.Failed -> Color(0xFFCF6679)    // Error Red
        else -> Color.Gray
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun PrimaryActionButton(
    isTracking: Boolean,
    isConnected: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Button(
        onClick = if (isTracking) onStop else onStart,
        enabled = isConnected,
        modifier = Modifier.fillMaxWidth(0.9f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTracking) "STOP LOG" else "START LOG",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun TelemetryCard(batch: RawSensorBatch) {
    Card(
        onClick = { /* No-op */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeFormatter.format(Date(batch.batchId)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val sizeBytes = (batch.ppgGreen.size + batch.accX.size + batch.skinTemp.size) * 4
                Text(
                    text = "${sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            CustomDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("PPG", "${batch.ppgGreen.size}")
                StatItem("ACC", "${batch.accX.size}")
                StatItem("TMP", "${batch.skinTemp.size}")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionErrorScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Warning",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Permissions Denied",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Body Sensors access is required to stream raw data.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CustomDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}