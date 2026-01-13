package com.samsung.health.mobile

import android.util.Log
import com.samsung.health.mobile.pipeline.PipelineResult
import com.samsung.health.mobile.pipeline.SignalProcessing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.min

@Singleton
class HealthDataRepository @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val signalProcessing = SignalProcessing()
    private val _healthData = MutableStateFlow(HealthSnapshot())
    val healthData = _healthData.asStateFlow()

    private val playbackQueue = ConcurrentLinkedQueue<PlaybackItem>()

    // --- PERSISTENCE STATE (Sample & Hold) ---
    private var lastValidHr = 0
    private var lastValidSpo2 = 0
    private var lastValidTemp = 0f
    private var lastValidRr = 0 // <--- ADDED RESP

    private var lastHrTime = 0L
    private var lastSpo2Time = 0L
    private var lastTempTime = 0L
    private var lastRrTime = 0L // <--- ADDED RESP

    // History buffers
    private val hrHistoryBuffer = mutableListOf<Float>()

    private val DEFAULT_TIMEOUT_MS = 2 * 60 * 1000L // 2 Minutes for HR/SpO2/Resp
    private val TEMP_TIMEOUT_MS = 3 * 60 * 1000L    // 3 Minutes for Temp (Requested)

    data class PlaybackItem(val result: PipelineResult, val delayAfter: Long)

    init {
        startStreamLoop()
    }

    fun processAndQueueBatch(batch: RawSensorBatch) {
        scope.launch {
            try {
                val results = signalProcessing.processBatch(batch)
                if (results.isNotEmpty()) {
                    val interval = (batch.durationMs / results.size).toLong()
                    results.forEach { res -> playbackQueue.add(PlaybackItem(res, interval)) }
                }
            } catch (e: Exception) {
                Log.e("HealthRepo", "❌ Pipeline Error", e)
            }
        }
    }

    private fun startStreamLoop() {
        scope.launch {
            val ecgBuffer = MutableList(100) { 0f }

            while (isActive) {
                val realItem = playbackQueue.poll()
                val now = System.currentTimeMillis()

                // 1. Sim ECG
                val newEcgPoints = SimulatedDataProvider.getEcgBatch(10)
                if (ecgBuffer.size > 100) ecgBuffer.subList(0, newEcgPoints.size).clear()
                ecgBuffer.addAll(newEcgPoints)

                // 2. Process Real Data
                if (realItem != null) {
                    val res = realItem.result

                    // --- HEART RATE ---
                    if (!res.hr.isNaN() && res.hr > 0) {
                        lastValidHr = res.hr.roundToInt()
                        lastHrTime = now
                        hrHistoryBuffer.add(lastValidHr.toFloat())
                        if (hrHistoryBuffer.size > 500) hrHistoryBuffer.removeAt(0)
                    }

                    // --- SPO2 ---
                    if (!res.spo2.isNaN() && res.spo2 > 0) {
                        var rawSpo2 = res.spo2.roundToInt()
                        if (rawSpo2 > 99) rawSpo2 = 99 // <--- CAP AT 99
                        lastValidSpo2 = rawSpo2
                        lastSpo2Time = now
                    }

                    // --- TEMP ---
                    if (res.skinTemperature > 0) {
                        lastValidTemp = res.skinTemperature.toFloat()
                        lastTempTime = now
                    }

                    // --- RESP ---
                    if (!res.rr.isNaN() && res.rr > 0) {
                        lastValidRr = res.rr.roundToInt()
                        lastRrTime = now
                    }

                    // --- MOVEMENT & IBI (Instant) ---
                    val movement = res.movement.toFloat()
                    val ibi = if (res.ibi.isNotEmpty()) res.ibi.last().toFloat() else 0f

                    // 3. Emit Snapshot
                    val snapshot = HealthSnapshot(
                        timestamp = res.timestamp,
                        heartRate = getHeldValue(lastValidHr, lastHrTime, now, DEFAULT_TIMEOUT_MS),
                        heartRateHistory = hrHistoryBuffer.toList(),
                        spo2 = getHeldValue(lastValidSpo2, lastSpo2Time, now, DEFAULT_TIMEOUT_MS),
                        skinTemperature = getHeldValue(lastValidTemp, lastTempTime, now, TEMP_TIMEOUT_MS), // 3 MIN TIMEOUT
                        respirationRate = getHeldValue(lastValidRr, lastRrTime, now, DEFAULT_TIMEOUT_MS), // 2 MIN TIMEOUT
                        ibi = ibi,
                        accMagnitude = movement,
                        ecgSignal = ecgBuffer.toList()
                    )

                    _healthData.emit(snapshot)
                    delay(realItem.delayAfter)

                } else {
                    // --- IDLE STATE ---
                    val snapshot = HealthSnapshot(
                        timestamp = now,
                        heartRate = getHeldValue(lastValidHr, lastHrTime, now, DEFAULT_TIMEOUT_MS),
                        heartRateHistory = hrHistoryBuffer.toList(),
                        spo2 = getHeldValue(lastValidSpo2, lastSpo2Time, now, DEFAULT_TIMEOUT_MS),
                        skinTemperature = getHeldValue(lastValidTemp, lastTempTime, now, TEMP_TIMEOUT_MS), // 3 MIN TIMEOUT
                        respirationRate = getHeldValue(lastValidRr, lastRrTime, now, DEFAULT_TIMEOUT_MS), // 2 MIN TIMEOUT
                        ibi = 0f,
                        accMagnitude = 0f,
                        ecgSignal = ecgBuffer.toList()
                    )

                    _healthData.emit(snapshot)
                    delay(40)
                }
            }
        }
    }

    private fun getHeldValue(value: Int, lastTime: Long, now: Long, timeout: Long): Int {
        return if ((now - lastTime) < timeout) value else 0
    }

    private fun getHeldValue(value: Float, lastTime: Long, now: Long, timeout: Long): Float {
        return if ((now - lastTime) < timeout) value else 0f
    }
}