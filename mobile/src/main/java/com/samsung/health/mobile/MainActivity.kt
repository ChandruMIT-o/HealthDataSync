package com.samsung.health.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ReceiverScreen(viewModel)
            }
        }
    }
}

@Composable
fun ReceiverScreen(viewModel: MainViewModel) {
    val fileSize by viewModel.fileSize.collectAsState()
    val lastUpdate by viewModel.lastUpdate.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(exportStatus) {
        exportStatus?.let {
            if (it != "Exporting...") Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Connection Indicator
        Icon(
            imageVector = if (isConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF00B894) else Color(0xFFD63031),
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isConnected) "Watch Connected" else "Waiting for Watch...",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = lastUpdate,
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(48.dp))

        // 2. Data Volume
        Text(text = "Total Data Stored", color = Color.Gray, fontSize = 14.sp)
        Text(
            text = fileSize,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(48.dp))

        // 3. Export Button
        Button(
            onClick = { viewModel.exportData() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0984E3))
        ) {
            Icon(Icons.Rounded.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("EXPORT CSV TO DOWNLOADS")
        }

        Spacer(Modifier.height(16.dp))

        // 4. Clear Button
        OutlinedButton(
            onClick = { viewModel.clearData() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD63031))
        ) {
            Icon(Icons.Rounded.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("CLEAR STORAGE")
        }
    }
}