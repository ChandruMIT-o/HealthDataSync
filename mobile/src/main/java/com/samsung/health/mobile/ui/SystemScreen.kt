package com.samsung.health.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.health.mobile.util.PerformanceMonitor

// --- SAMSUNG ONE UI PALETTE ---
private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)
private val AccentPurple = Color(0xFFD0BCFF)
private val AccentRed = Color(0xFFFF8A80)
private val AccentBlue = Color(0xFF82B1FF)
private val AccentGreen = Color(0xFFB9F6CA)

@Composable
fun SystemScreen() {
    val scrollState = rememberScrollState()
    val stats by PerformanceMonitor.stats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("System Monitor", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // --- 1. POWER BREAKDOWN (Updated with Averages) ---
        SystemCard(title = "App Power Impact", icon = Icons.Rounded.Bolt, color = AccentRed) {
            Column {
                // A. Total App Summary
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Instant Power", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(stats.appTotalWatts, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Session Avg", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        // NEW: Showing Average Total Watts
                        Text(stats.avgTotalWatts, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Total Drain: ${stats.appTotalDrain}", style = MaterialTheme.typography.labelMedium, color = AccentRed, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                // B. The Split (Updated to show averages)
                Text("Consumption Breakdown", style = MaterialTheme.typography.labelMedium, color = TextSubtle)
                Spacer(Modifier.height(12.dp))

                // UI ROW
                SplitRow(
                    label = "UI Rendering",
                    instant = stats.appUiWatts,
                    average = stats.avgUiWatts, // NEW
                    drain = stats.appUiDrain,
                    color = AccentBlue,
                    percent = stats.uiCpuUsagePercent
                )

                Spacer(Modifier.height(12.dp))

                // BACKGROUND ROW
                SplitRow(
                    label = "Background Processes",
                    instant = stats.appBgWatts,
                    average = stats.avgBgWatts, // NEW
                    drain = stats.appBgDrain,
                    color = AccentPurple,
                    percent = stats.bgCpuUsagePercent
                )
            }
        }

        // --- 2. CONNECTION HEALTH (Latency) ---
        SystemCard(title = "Connection Health", icon = Icons.Rounded.Storage, color = AccentBlue) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Transmission Latency", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(stats.packetLatency, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if(stats.packetLatency.startsWith("Wait")) TextSubtle else AccentGreen)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Session Received", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(stats.totalRx, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "* Latency = Time Diff between Watch (Send) and Phone (Receive). Lower is better.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
        }

        // --- 3. RAM ---
        SystemCard(title = "App RAM Usage", icon = Icons.Rounded.Memory, color = AccentPurple) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(text = stats.totalPss, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(text = "Total Allocated", style = MaterialTheme.typography.bodyMedium, color = TextSubtle)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))
                StatRow("Background Logic (Java/Kotlin)", stats.javaHeap)
                StatRow("Native Processing (C++/JNI)", stats.nativeHeap)
                StatRow("Rendering & Graphics", stats.graphicsMem)
            }
        }

        // --- 4. CPU ---
        SystemCard(title = "Processor Impact", icon = Icons.Rounded.Bolt, color = AccentRed) {
            Column {
                Text("Real-time Processor Load", style = MaterialTheme.typography.labelMedium, color = TextSubtle)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { stats.cpuLoad },
                    modifier = Modifier.fillMaxWidth().height(8.dp).background(Color.DarkGray, RoundedCornerShape(4.dp)),
                    color = if (stats.cpuLoad > 0.5f) Color.Red else AccentBlue,
                    trackColor = Color.Transparent,
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Active CPU", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(stats.cpuTimeUser, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Kernel/Sys", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(stats.cpuTimeSystem, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = TextSubtle, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Session: ${stats.upTime}", style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                }
            }
        }

        // Whole Device Context
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = TextSubtle, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Whole Device Draw: ${stats.currentWatts}", color = TextSubtle, fontSize = 12.sp)
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

@Composable
fun SystemCard(title: String, icon: ImageVector, color: Color, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSubtle)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextWhite)
    }
}

@Composable
fun SplitRow(
    label: String,
    instant: String,
    average: String,
    drain: String,
    color: Color,
    percent: Float
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Color Indicator
        Box(modifier = Modifier.size(4.dp, 40.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextWhite)
            // Tiny progress bar relative to total app usage
            LinearProgressIndicator(
                progress = { percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).padding(top=6.dp).clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = Color.DarkGray,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            // Row for Instant / Average
            Row(verticalAlignment = Alignment.Bottom) {
                Text(instant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(Modifier.width(4.dp))
                Text("($average avg)", style = MaterialTheme.typography.labelSmall, color = TextSubtle, fontSize = 10.sp)
            }
            Text(drain, style = MaterialTheme.typography.labelSmall, color = TextSubtle)
        }
    }
}