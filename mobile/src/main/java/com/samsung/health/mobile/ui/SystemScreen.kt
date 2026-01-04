// --- src/main/java/com/samsung/health/mobile/ui/SystemScreen.kt ---
package com.samsung.health.mobile.ui

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// --- SAMSUNG ONE UI PALETTE ---
private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)
private val AccentPurple = Color(0xFFD0BCFF)
private val AccentRed = Color(0xFFFF8A80)
private val AccentBlue = Color(0xFF82B1FF)

@Composable
fun SystemScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // --- STATE HOLDERS ---
    var totalPss by remember { mutableStateOf("0 MB") }
    var javaHeap by remember { mutableStateOf("0 MB") }
    var nativeHeap by remember { mutableStateOf("0 MB") }
    var graphicsMem by remember { mutableStateOf("0 MB") }

    // CPU / Battery Proxy
    var cpuTimeUser by remember { mutableStateOf("0s") }
    var cpuTimeSystem by remember { mutableStateOf("0s") }
    var upTime by remember { mutableStateOf("0s") }
    var cpuLoadIndicator by remember { mutableFloatStateOf(0f) }

    // --- LIVE MONITORING LOOP ---
    LaunchedEffect(Unit) {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val myPid = Process.myPid()
        val startTime = System.currentTimeMillis()

        while (true) {
            // 1. GET RAM METRICS (App Specific)
            // We verify pid to ensure we are looking at our own process
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(myPid))[0]

            val total = memInfo.totalPss / 1024f // Convert kB to MB
            val java = memInfo.dalvikPss / 1024f
            val native = memInfo.nativePss / 1024f

            // Graphics memory is often hidden in 'other' or native on modern Android
            // We estimate it by subtracting known heaps from total, which usually leaves GL/EGL buffers
            val graphics = (memInfo.totalPss - memInfo.dalvikPss - memInfo.nativePss) / 1024f

            totalPss = "%.1f MB".format(total)
            javaHeap = "%.1f MB".format(java)
            nativeHeap = "%.1f MB".format(native)
            graphicsMem = "%.1f MB".format(graphics.coerceAtLeast(0f))

            // 2. GET CPU STATS FROM LINUX KERNEL (/proc/self/stat)
            // This replaces Os.getrusage and works on ALL Android versions
            val (utimeTicks, stimeTicks) = readKernelCpuStats()

            // Android typically uses 100 ticks per second (USER_HZ)
            val userSec = utimeTicks / 100.0
            val sysSec = stimeTicks / 100.0

            cpuTimeUser = "%.2fs".format(userSec)
            cpuTimeSystem = "%.2fs".format(sysSec)

            // Calculate Load
            val wallTimeSec = (System.currentTimeMillis() - startTime) / 1000.0
            if (wallTimeSec > 0) {
                val totalCpuSec = userSec + sysSec
                // Ratio of CPU time consumed vs Real time elapsed
                val load = (totalCpuSec / wallTimeSec).toFloat()
                cpuLoadIndicator = load.coerceIn(0f, 1f)
            }

            upTime = "%dm %ds".format(wallTimeSec.toInt() / 60, wallTimeSec.toInt() % 60)

            delay(1000) // Update every second
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "App Performance",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // --- 1. MEMORY USAGE (RAM) ---
        SystemCard(title = "App RAM Usage", icon = Icons.Rounded.Memory, color = AccentPurple) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(text = totalPss, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(text = "Total Allocated", style = MaterialTheme.typography.bodyMedium, color = TextSubtle)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                // Detailed Breakdown
                StatRow("Background Logic (Java/Kotlin)", javaHeap)
                StatRow("Native Processing (C++/JNI)", nativeHeap)
                StatRow("Rendering & Graphics", graphicsMem)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "* 'Background Logic' is your DataReceiverService.\n* 'Graphics' is UI rendering cost.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
        }

        // --- 2. BATTERY & CPU ---
        SystemCard(title = "Power & CPU Impact", icon = Icons.Rounded.Bolt, color = AccentRed) {
            Column {
                // CPU Load Gauge
                Text("Real-time Processor Load", style = MaterialTheme.typography.labelMedium, color = TextSubtle)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { cpuLoadIndicator },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color.DarkGray, RoundedCornerShape(4.dp)),
                    color = if (cpuLoadIndicator > 0.5f) Color.Red else AccentBlue,
                    trackColor = Color.Transparent,
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // CPU User Time
                    Column {
                        Text("Active CPU Time", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(cpuTimeUser, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
                        Text("Logic & Calculation", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    // CPU System Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text("System CPU Time", style = MaterialTheme.typography.labelSmall, color = TextSubtle)
                        Text(cpuTimeSystem, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextWhite)
                        Text("Kernel & I/O", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Color(0xFF263238),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Speed, null, tint = TextSubtle, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Tracking session duration: $upTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                    }
                }
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

// Helper to read Linux Kernel Stats directly
private suspend fun readKernelCpuStats(): Pair<Long, Long> = withContext(Dispatchers.IO) {
    try {
        // /proc/self/stat contains process info. Fields 13 and 14 are utime and stime.
        val statFile = File("/proc/self/stat")
        if (statFile.exists()) {
            val content = statFile.readText()
            val tokens = content.split(" ")
            if (tokens.size > 14) {
                // Token 13 = utime (User Time)
                // Token 14 = stime (System Time)
                val utime = tokens[13].toLong()
                val stime = tokens[14].toLong()
                return@withContext Pair(utime, stime)
            }
        }
    } catch (e: Exception) {
        // Fallback if permission denied (rare on self)
    }
    return@withContext Pair(0L, 0L)
}

@Composable
fun SystemCard(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable () -> Unit
) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSubtle)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextWhite)
    }
}