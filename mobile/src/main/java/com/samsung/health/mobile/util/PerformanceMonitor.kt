// --- src/main/java/com/samsung/health/mobile/util/PerformanceMonitor.kt ---
package com.samsung.health.mobile.util

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Process
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

object PerformanceMonitor {

    data class SystemStats(
        // RAM
        val totalPss: String = "0 MB",
        val javaHeap: String = "0 MB",
        val nativeHeap: String = "0 MB",
        val graphicsMem: String = "0 MB",

        // Power - Instant
        val currentWatts: String = "0.0 W",
        val appTotalWatts: String = "0.000 W",
        val appUiWatts: String = "0.000 W",
        val appBgWatts: String = "0.000 W",

        // Power - Session Averages (NEW)
        val avgTotalWatts: String = "0.000 W",
        val avgUiWatts: String = "0.000 W",
        val avgBgWatts: String = "0.000 W",

        // Drain - Cumulative Session
        val appTotalDrain: String = "0.00%",
        val appUiDrain: String = "0.00%",
        val appBgDrain: String = "0.00%",

        // Network (Latency)
        val packetLatency: String = "-- ms",
        val totalRx: String = "0 KB",

        // CPU
        val cpuLoad: Float = 0f,
        val cpuTimeUser: String = "0s",
        val cpuTimeSystem: String = "0s",
        val uiCpuUsagePercent: Float = 0f,
        val bgCpuUsagePercent: Float = 0f,
        val upTime: String = "0s"
    )

    private val _stats = MutableStateFlow(SystemStats())
    val stats = _stats.asStateFlow()

    private var job: Job? = null
    private var startTime = 0L

    // Config
    private val PHONE_BATTERY_WH = 19.25
    private val MAX_CPU_POWER_W = 3.5

    // Accumulators for Energy (Wh)
    private var uiAccumulatedWh = 0.0
    private var bgAccumulatedWh = 0.0

    // Accumulators for Averages
    private var sumAppTotalWatts = 0.0
    private var sumUiWatts = 0.0
    private var sumBgWatts = 0.0
    private var sampleCount = 0L

    // Network State
    private var manualRxBytes = 0L
    private var lastLatencyMs = -1L

    // CPU State
    private var lastTotalCpuTime = 0L
    private var lastMainThreadCpuTime = 0L
    private var lastLoopTime = 0L

    fun logPacket(bytesSize: Long, sendTimestamp: Long) {
        val now = System.currentTimeMillis()
        manualRxBytes += bytesSize
        lastLatencyMs = (now - sendTimestamp).coerceAtLeast(0)
    }

    fun start(context: Context) {
        if (job?.isActive == true) return
        startTime = System.currentTimeMillis()
        lastLoopTime = System.currentTimeMillis()

        // Reset all accumulators
        uiAccumulatedWh = 0.0
        bgAccumulatedWh = 0.0
        sumAppTotalWatts = 0.0
        sumUiWatts = 0.0
        sumBgWatts = 0.0
        sampleCount = 0L

        manualRxBytes = 0L
        lastLatencyMs = -1L

        job = CoroutineScope(Dispatchers.IO).launch {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val myPid = Process.myPid()
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

            while (isActive) {
                val now = System.currentTimeMillis()
                val timeDeltaSec = (now - lastLoopTime) / 1000.0
                val safeTimeDelta = if (timeDeltaSec < 0.1) 0.1 else timeDeltaSec
                lastLoopTime = now

                // --- 1. CPU LOGIC ---
                val (procUtime, procStime) = readCpuStat("/proc/self/stat")
                val (mainUtime, mainStime) = readCpuStat("/proc/self/task/$myPid/stat")

                val currentTotalCpu = procUtime + procStime
                val currentMainCpu = mainUtime + mainStime
                val deltaTotal = currentTotalCpu - lastTotalCpuTime
                val deltaMain = currentMainCpu - lastMainThreadCpuTime

                var uiRatio = 0.0f
                var bgRatio = 0.0f
                if (deltaTotal > 0) {
                    uiRatio = deltaMain.toFloat() / deltaTotal.toFloat()
                    bgRatio = 1f - uiRatio
                }

                val activeCpuSec = deltaTotal / 100.0
                val instantLoad = (activeCpuSec / safeTimeDelta).coerceIn(0.0, 1.0)

                lastTotalCpuTime = currentTotalCpu
                lastMainThreadCpuTime = currentMainCpu
                val userSec = procUtime / 100.0
                val sysSec = procStime / 100.0

                // --- 2. MEMORY LOGIC ---
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(myPid))[0]
                val totalMem = memInfo.totalPss / 1024f
                val javaMem = memInfo.dalvikPss / 1024f
                val nativeMem = memInfo.nativePss / 1024f
                val graphicsMem = (memInfo.totalPss - memInfo.dalvikPss - memInfo.nativePss) / 1024f

                // --- 3. NETWORK LOGIC ---
                val totalReceivedKb = manualRxBytes / 1024f
                val latencyDisplay = if (lastLatencyMs >= 0) "$lastLatencyMs ms" else "Waiting..."

                // --- 4. POWER LOGIC ---
                // A. Real Device Power
                val currentNowMicroA = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                val totalDeviceWatts = abs((voltageMv / 1000f) * (currentNowMicroA / 1000000f))

                // B. Modeled App Power
                val appTotalWatts = (instantLoad * MAX_CPU_POWER_W) + 0.02
                val uiWatts = appTotalWatts * uiRatio
                val bgWatts = appTotalWatts * bgRatio

                // C. Accumulate Energy (for Drain %)
                val hourFactor = safeTimeDelta / 3600.0
                uiAccumulatedWh += (uiWatts * hourFactor)
                bgAccumulatedWh += (bgWatts * hourFactor)
                val totalAppWh = uiAccumulatedWh + bgAccumulatedWh

                val totalDrain = (totalAppWh / PHONE_BATTERY_WH) * 100
                val uiDrain = (uiAccumulatedWh / PHONE_BATTERY_WH) * 100
                val bgDrain = (bgAccumulatedWh / PHONE_BATTERY_WH) * 100

                // D. Calculate Running Averages
                sumAppTotalWatts += appTotalWatts
                sumUiWatts += uiWatts
                sumBgWatts += bgWatts
                sampleCount++

                val avgAppTotal = if(sampleCount > 0) sumAppTotalWatts / sampleCount else 0.0
                val avgUi = if(sampleCount > 0) sumUiWatts / sampleCount else 0.0
                val avgBg = if(sampleCount > 0) sumBgWatts / sampleCount else 0.0

                // --- UPDATE STATE ---
                val wallTimeSec = (System.currentTimeMillis() - startTime) / 1000.0

                _stats.value = SystemStats(
                    totalPss = "%.1f MB".format(totalMem),
                    javaHeap = "%.1f MB".format(javaMem),
                    nativeHeap = "%.1f MB".format(nativeMem),
                    graphicsMem = "%.1f MB".format(graphicsMem.coerceAtLeast(0f)),

                    currentWatts = "%.2f W".format(totalDeviceWatts),
                    appTotalWatts = "%.3f W".format(appTotalWatts),
                    appUiWatts = "%.3f W".format(uiWatts),
                    appBgWatts = "%.3f W".format(bgWatts),

                    // NEW: Averages
                    avgTotalWatts = "%.3f W".format(avgAppTotal),
                    avgUiWatts = "%.3f W".format(avgUi),
                    avgBgWatts = "%.3f W".format(avgBg),

                    appTotalDrain = "-%.4f%%".format(totalDrain),
                    appUiDrain = "-%.4f%%".format(uiDrain),
                    appBgDrain = "-%.4f%%".format(bgDrain),

                    packetLatency = latencyDisplay,
                    totalRx = "%.0f KB".format(totalReceivedKb),

                    cpuLoad = instantLoad.toFloat(),
                    cpuTimeUser = "%.2fs".format(userSec),
                    cpuTimeSystem = "%.2fs".format(sysSec),
                    uiCpuUsagePercent = uiRatio,
                    bgCpuUsagePercent = bgRatio,
                    upTime = "%dm %ds".format(wallTimeSec.toInt() / 60, wallTimeSec.toInt() % 60)
                )

                delay(1000)
            }
        }
    }

    private fun readCpuStat(path: String): Pair<Long, Long> {
        return try {
            val file = File(path)
            if (file.exists()) {
                val tokens = file.readText().split(" ")
                if (tokens.size > 14) Pair(tokens[13].toLong(), tokens[14].toLong()) else Pair(0L, 0L)
            } else Pair(0L, 0L)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }
}