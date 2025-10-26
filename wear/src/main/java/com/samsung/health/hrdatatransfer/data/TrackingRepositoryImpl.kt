package com.samsung.health.hrdatatransfer.data
import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.data.ValueKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow // --- FIX: Import
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update // --- FIX: Import
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.random.Random
private const val TAG = "TrackingRepositoryImpl"
private const val SNAPSHOT_INTERVAL_MS = 1000L // 1 Hz
private const val DATA_STALE_MS = 60_000L // 1 minute
@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val healthTrackingServiceConnection: HealthTrackingServiceConnection,
    @ApplicationContext private val context: Context,
) : TrackingRepository {
    private var healthTrackingService: HealthTrackingService? = null
    private val activeTrackers = mutableMapOf<HealthTrackerType, HealthTracker>()

    // --- FIX: Replace the non-thread-safe variable with an atomic MutableStateFlow
    private val latestRecordFlow = MutableStateFlow(HealthDataRecord())
    // --- END FIX ---

    @Volatile
    private var lastSensorUpdateTimestamps = mutableMapOf<String, Long>()
    private var simEda = 0.8f
    private var simulationTimeStep = 0.0
    override fun stopTracking() {
        Log.i(TAG, "Stopping all trackers.")
        activeTrackers.values.forEach { it.unsetEventListener() }
        activeTrackers.clear()
        latestRecordFlow.value = HealthDataRecord() // --- FIX: Reset the flow's value
        lastSensorUpdateTimestamps.clear() // Clear timestamps
    }
    override fun track(trackerTypes: Set<HealthTrackerType>): Flow<HealthDataRecord> = callbackFlow {
        healthTrackingService = healthTrackingServiceConnection.getHealthTrackingService()
        if (healthTrackingService == null) {
            Log.e(TAG, "Health Tracking Service is not available. Cannot start tracking.")
            close(IllegalStateException("Health Tracking Service is not available."))
            return@callbackFlow
        }
        latestRecordFlow.value = HealthDataRecord() // --- FIX: Reset the flow's value
        lastSensorUpdateTimestamps.clear()
        simulationTimeStep = 0.0
        simEda = 0.8f + (Random.nextFloat() - 0.5f) * 0.1f
        trackerTypes.forEach { type ->
            if (activeTrackers[type] == null) {
                Log.d(TAG, "Attempting to initialize tracker: $type")
                try {
                    val tracker: HealthTracker
                    if (type == HealthTrackerType.PPG_CONTINUOUS) {
                        Log.d(TAG, "Using special overload for PPG_CONTINUOUS")
                        val ppgTypes = setOf(PpgType.GREEN, PpgType.IR, PpgType.RED)
                        tracker = healthTrackingService!!.getHealthTracker(type, ppgTypes)
                    } else {
                        tracker = healthTrackingService!!.getHealthTracker(type)
                    }
                    tracker.setEventListener(createEventListenerForType(type))
                    activeTrackers[type] = tracker
                    Log.i(TAG, "✅ Successfully started tracker for $type")
                } catch (e: Exception) {
                    when (e) {
                        is IllegalArgumentException -> {
                            Log.e(
                                TAG,
                                "❌ FAILED (Invalid Argument/Type): $type. Reason: ${e.message}",
                                e
                            )
                        }
                        is UnsupportedOperationException -> {
                            Log.w(
                                TAG,
                                "⚠️ SKIPPING (Not Supported): $type. Reason: ${e.message}"
                            )
                        }
                        else -> {
                            Log.e(
                                TAG,
                                "❌ FAILED (Unknown): $type. Reason: ${e.message}",
                                e
                            )
                        }
                    }
                }
            }
        }
        val snapshotLoop = launch(Dispatchers.Default) {
            while (isActive) {
                val realTimeData = latestRecordFlow.value // --- FIX: Get value from flow
                val now = Instant.now().toEpochMilli()
                if (realTimeData.hr != null && realTimeData.hr == 0) {
                    Log.w(TAG, "Off-wrist detected (Real HR == 0). Skipping send.")
                    delay(SNAPSHOT_INTERVAL_MS)
                    continue
                }
                updateSimulatedValues()
                val snapshot = HealthDataRecord(
                    timestamp = now,
                    hr = getStaleData(ValueKey.HeartRateSet.HEART_RATE.toString(), now, realTimeData.hr, 0),
                    ibi = getStaleData(ValueKey.HeartRateSet.IBI_LIST.toString(), now, realTimeData.ibi, null),
                    ppgGreen = getStaleData(ValueKey.PpgSet.PPG_GREEN.toString(), now, realTimeData.ppgGreen, 0),
                    ppgRed = getStaleData(ValueKey.PpgSet.PPG_RED.toString(), now, realTimeData.ppgRed, 0),
                    ppgIr = getStaleData(ValueKey.PpgSet.PPG_IR.toString(), now, realTimeData.ppgIr, 0),
                    accX = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_X.toString(), now, realTimeData.accX, 0),
                    accY = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_Y.toString(), now, realTimeData.accY, 0),
                    accZ = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_Z.toString(), now, realTimeData.accZ, 0),
                    skinTemp = getStaleData(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE.toString(), now, realTimeData.skinTemp, 0.0f),
                    eda = realTimeData.eda ?: simEda,
                    ecg = realTimeData.ecg ?: (sin(simulationTimeStep * 2) * 500 + (Random.nextFloat() - 0.5f) * 25).toFloat()
                )
                trySendBlocking(snapshot)
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
        awaitClose {
            Log.i(TAG, "Flow is closing. Cancelling snapshot loop and unsetting listeners.")
            snapshotLoop.cancel()
            activeTrackers.values.forEach { it.unsetEventListener() }
            activeTrackers.clear()
            lastSensorUpdateTimestamps.clear()
        }
    }
    private fun <T> getStaleData(key: String, now: Long, lastValue: T, defaultValue: T): T {
        val lastUpdate = lastSensorUpdateTimestamps[key] ?: 0L
        return if (now - lastUpdate < DATA_STALE_MS) {
            lastValue
        } else {
            defaultValue
        }
    }
    private fun updateSimulatedValues() {
        simulationTimeStep += 0.1 // This loop runs every 1s, so increase is slow
        if (activeTrackers[HealthTrackerType.EDA_CONTINUOUS] == null) {
            simEda += (Random.nextFloat() - 0.5f) * 0.01f
            if (simEda < 0.1f) simEda = 0.1f
        }
    }
    private fun createEventListenerForType(trackerType: HealthTrackerType): HealthTracker.TrackerEventListener {
        return object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                Log.d(TAG, "onDataReceived for $trackerType. DataPoints: ${dataPoints.size}")
                val now = Instant.now().toEpochMilli()
                for (dataPoint in dataPoints) {
                    try {
                        // --- FIX: Use atomic .update { ... } for all sensor writes
                        when (trackerType) {
                            HealthTrackerType.PPG_CONTINUOUS -> {
                                latestRecordFlow.update {
                                    it.copy(
                                        ppgGreen = dataPoint.getValue(ValueKey.PpgSet.PPG_GREEN),
                                        ppgIr = dataPoint.getValue(ValueKey.PpgSet.PPG_IR),
                                        ppgRed = dataPoint.getValue(ValueKey.PpgSet.PPG_RED)
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_GREEN.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_IR.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_RED.toString()] = now
                            }
                            HealthTrackerType.HEART_RATE_CONTINUOUS -> {
                                val ibiList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                                val hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                                Log.i(TAG, "Real HR received: $hr")
                                latestRecordFlow.update {
                                    it.copy(
                                        hr = hr,
                                        ibi = ibiList?.let { ArrayList(it) }
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.HeartRateSet.HEART_RATE.toString()] = now
                                if (ibiList != null) {
                                    lastSensorUpdateTimestamps[ValueKey.HeartRateSet.IBI_LIST.toString()] = now
                                }
                            }
                            HealthTrackerType.ACCELEROMETER_CONTINUOUS -> {
                                latestRecordFlow.update {
                                    it.copy(
                                        accX = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X),
                                        accY = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y),
                                        accZ = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z)
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.AccelerometerSet.ACCELEROMETER_X.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.AccelerometerSet.ACCELEROMETER_Y.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.AccelerometerSet.ACCELEROMETER_Z.toString()] = now
                            }
                            HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS -> {
                                val temp =
                                    dataPoint.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                                latestRecordFlow.update {
                                    it.copy(
                                        skinTemp = temp
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE.toString()] = now
                            }
                            HealthTrackerType.EDA_CONTINUOUS -> {
                                latestRecordFlow.update {
                                    it.copy(
                                        eda = dataPoint.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE)
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.EdaSet.SKIN_CONDUCTANCE.toString()] = now
                            }
                            else -> {}
                        }
                        // --- END FIX
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing dataPoint in onDataReceived", e)
                    }
                }
            }
            override fun onFlushCompleted() {
                Log.i(TAG, "onFlushCompleted for $trackerType")
            }
            override fun onError(error: HealthTracker.TrackerError) {
                Log.e(TAG, "🔴 RUNTIME ERROR for $trackerType: $error")
            }
        }
    }
}