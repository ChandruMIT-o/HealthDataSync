// --- src/main/java/com/samsung/health/mobile/ui/DataScreen.kt ---
package com.samsung.health.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.health.mobile.MainViewModel

// --- THEME ---
private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)
private val ActionBlue = Color(0xFF5AB6F7)
private val AlertRed = Color(0xFFFF5252)

@Composable
fun DataScreen(viewModel: MainViewModel) {
    val fileSize by viewModel.fileSize.collectAsState()
    val lastUpdate by viewModel.lastUpdate.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- 1. CONNECTION STATUS ---
        Card(
            colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Rounded.Watch else Icons.Rounded.WatchOff,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF65D46E) else AlertRed,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isConnected) "Watch Connected" else "Searching...",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = lastUpdate,
                        color = TextSubtle,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // --- 2. STORAGE STATUS ---
        Card(
            colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.FolderZip,
                    null,
                    tint = TextSubtle,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = fileSize,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Total Storage Used",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSubtle
                )
            }
        }

        Spacer(Modifier.weight(1f)) // Push buttons to bottom

        // --- 3. ACTIONS ---
        // Export Button (Blue)
        ActionButton(
            text = "Export to Downloads",
            icon = Icons.Rounded.Download,
            color = ActionBlue,
            onClick = { viewModel.exportData() }
        )

        // Clear Button (Red/Outline)
        OutlinedButton(
            onClick = { viewModel.clearData() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
        ) {
            Icon(Icons.Rounded.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("Clear All Data", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Icon(icon, null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}