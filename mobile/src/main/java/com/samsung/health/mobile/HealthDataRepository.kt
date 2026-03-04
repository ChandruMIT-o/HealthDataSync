package com.samsung.health.mobile

import android.util.Log
import com.samsung.health.mobile.pipeline.PipelineResult
import com.samsung.health.mobile.pipeline.SignalProcessing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthDataRepository @Inject constructor(
    private val storageManager: StorageManager,
    private val firestoreManager: FirestoreManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val signalProcessing = SignalProcessing()

    private val _healthData = MutableStateFlow(HealthSnapshot())
    val healthData = _healthData.asStateFlow()

    private val playbackQueue = ConcurrentLinkedQueue<PlaybackItem>()

    // Local cached states
    private var lastValidHr = 0
    private var lastValidSpo2 = 0
    private var lastValidTemp = 0f
    private var lastValidRr = 0
    private var lastHrTime = 0L
    private var lastSpo2Time = 0L
    private var lastTempTime = 0L
    private var lastRrTime = 0L
    private val hrHistoryBuffer = mutableListOf<Float>()

    private val DEFAULT_TIMEOUT_MS = 2 * 60 * 1000L
    private val TEMP_TIMEOUT_MS = 3 * 60 * 1000L

    data class PlaybackItem(
        val result: PipelineResult,
        val delayAfter: Long
    )

    init {
        startStreamLoop()
        startMetricsListener()
    }

    /**
     * Mounts the Firestore listener to reactively update the ST-GAT Metrics.
     */
    private fun startMetricsListener() {
        firestoreManager.listenToTodayMetrics { docs ->
            val sqiList = mutableListOf<Float>()
            val psiList = mutableListOf<Float>()
            val clsList = mutableListOf<Float>()
            val cvhsList = mutableListOf<Float>()
            val evsList = mutableListOf<Float>()

            for (doc in docs) {
                // Assuming metrics format: [sqi, psi, cls, cvhs, evs]
                val metricsArray = doc["metrics"] as? List<*> ?: continue
                if (metricsArray.size == 5) {
                    try {
                        sqiList.add((metricsArray[0] as Number).toFloat())
                        psiList.add((metricsArray[1] as Number).toFloat())
                        clsList.add((metricsArray[2] as Number).toFloat())
                        cvhsList.add((metricsArray[3] as Number).toFloat())
                        evsList.add((metricsArray[4] as Number).toFloat())
                    } catch (e: Exception) {
                        Log.e("HealthRepo", "Error parsing metric data format", e)
                    }
                }
            }

            // Atomically apply the new arrays to the state flow
            _healthData.update { current ->
                current.copy(
                    sqiHistory = sqiList,
                    sqi = sqiList.lastOrNull() ?: current.sqi,

                    psiHistory = psiList,
                    psi = psiList.lastOrNull() ?: current.psi,

                    clsHistory = clsList,
                    cls = clsList.lastOrNull() ?: current.cls,

                    cvhsHistory = cvhsList,
                    cvhs = cvhsList.lastOrNull() ?: current.cvhs,

                    evsHistory = evsList,
                    evs = evsList.lastOrNull() ?: current.evs
                )
            }
        }
    }

    fun processAndQueueBatch(batch: RawSensorBatch) {
        scope.launch {
            try {
                val results = signalProcessing.processBatch(batch)
                if (results.isNotEmpty()) {
                    val interval = (batch.durationMs / results.size).toLong()
                    results.forEach { res ->
                        playbackQueue.add(PlaybackItem(res, interval))
                    }

                    val minuteBatches = results.segmentIntoFullResolutionBatches()
                    minuteBatches.forEach { minuteBatch ->
                        storageManager.saveProcessedBatch(minuteBatch)
                        firestoreManager.uploadMinuteBatch(minuteBatch)
                    }
                    Log.d("HealthRepo", "✅ Processed, Saved locally & Uploaded ${minuteBatches.size} min(s)")
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
                val item = playbackQueue.poll()
                val ecgBatch = SimulatedDataProvider.getEcgBatch(10)

                if (ecgBuffer.size > 100) {
                    ecgBuffer.subList(0, ecgBatch.size).clear()
                }
                ecgBuffer.addAll(ecgBatch)

                if (item != null) {
                    val res = item.result
                    val t = res.timestamp

                    if (!res.hr.isNaN() && res.hr > 0) {
                        lastValidHr = res.hr.roundToInt()
                        lastHrTime = t
                        hrHistoryBuffer.add(lastValidHr.toFloat())
                        if (hrHistoryBuffer.size > 500) hrHistoryBuffer.removeAt(0)
                    }
                    if (!res.spo2.isNaN() && res.spo2 > 0) {
                        var spo2 = res.spo2.roundToInt()
                        if (spo2 > 99) spo2 = 99
                        lastValidSpo2 = spo2
                        lastSpo2Time = t
                    }
                    if (res.skinTemperature > 0) {
                        lastValidTemp = res.skinTemperature.toFloat()
                        lastTempTime = t
                    }
                    if (!res.rr.isNaN() && res.rr > 0) {
                        lastValidRr = res.rr.roundToInt()
                        lastRrTime = t
                    }
                    val ibi = if (res.ibi.isNotEmpty()) res.ibi.last().toFloat() else 0f

                    _healthData.update { current ->
                        current.copy(
                            timestamp = t,
                            heartRate = getHeldValue(lastValidHr, lastHrTime, t, DEFAULT_TIMEOUT_MS),
                            heartRateHistory = hrHistoryBuffer.toList(),
                            spo2 = getHeldValue(lastValidSpo2, lastSpo2Time, t, DEFAULT_TIMEOUT_MS),
                            skinTemperature = getHeldValue(lastValidTemp, lastTempTime, t, TEMP_TIMEOUT_MS),
                            respirationRate = getHeldValue(lastValidRr, lastRrTime, t, DEFAULT_TIMEOUT_MS),
                            ibi = ibi,
                            accMagnitude = res.movement.toFloat(),
                            ecgSignal = ecgBuffer.toList()
                        )
                    }
                    delay(item.delayAfter)
                } else {
                    val now = System.currentTimeMillis()
                    _healthData.update { current ->
                        current.copy(
                            timestamp = now,
                            heartRate = getHeldValue(lastValidHr, lastHrTime, now, DEFAULT_TIMEOUT_MS),
                            heartRateHistory = hrHistoryBuffer.toList(),
                            spo2 = getHeldValue(lastValidSpo2, lastSpo2Time, now, DEFAULT_TIMEOUT_MS),
                            skinTemperature = getHeldValue(lastValidTemp, lastTempTime, now, TEMP_TIMEOUT_MS),
                            respirationRate = getHeldValue(lastValidRr, lastRrTime, now, DEFAULT_TIMEOUT_MS),
                            ibi = 0f,
                            accMagnitude = 0f,
                            ecgSignal = ecgBuffer.toList()
                        )
                    }
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

fun List<PipelineResult>.segmentIntoFullResolutionBatches(): List<MinuteBatch> {
    val grouped = this.groupBy { it.timestamp / 60_000L }
    return grouped.map { (minuteBucket, samples) ->
        val sorted = samples.sortedBy { it.timestamp }
        MinuteBatch(
            startTimestamp = minuteBucket * 60_000L,
            timestamps = sorted.map { it.timestamp },
            hrValues = sorted.map { it.hr },
            spo2Values = sorted.map { it.spo2 },
            rrValues = sorted.map { it.rr },
            movementValues = sorted.map { it.movement },
            tempValues = sorted.map { it.skinTemperature },
            ibiStream = sorted.flatMap { it.ibi }
        )
    }.sortedBy { it.startTimestamp }
}