package com.samsung.health.hrdatatransfer.data

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "TrackingRepositoryImpl"
private const val SNAPSHOT_INTERVAL_MS = 40L // 25 Hz

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val coroutineScope: CoroutineScope,
    private val healthTrackingServiceConnection: HealthTrackingServiceConnection,
    @ApplicationContext private val context: Context,
) : TrackingRepository {
    private var healthTrackingService: HealthTrackingService? = null
    private val activeTrackers = mutableMapOf<HealthTrackerType, HealthTracker>()

    @Volatile
    private var latestRecord = HealthDataRecord()
    private var snapshotJob: Job? = null

    // --- State variables for data simulation ---
    private var simHr = 82.5f
    private var simSkinTemp = 34.2f
    private var simEda = 0.8f
    private var simSpo2 = 98.5f
    private var simAccX = 0f
    private var simAccY = 9.8f
    private var simAccZ = 0f
    private var simulationTimeStep = 0.0

    override fun stopTracking() {
        Log.i(TAG, "Stopping all trackers.")
        snapshotJob?.cancel()
        activeTrackers.values.forEach { it.unsetEventListener() }
        activeTrackers.clear()
    }

    override fun track(trackerTypes: Set<HealthTrackerType>): Flow<HealthDataRecord> = callbackFlow {
        healthTrackingService = healthTrackingServiceConnection.getHealthTrackingService()
        if (healthTrackingService == null) {
            Log.e(TAG, "Health Tracking Service is not available. Cannot start tracking.")
            close(IllegalStateException("Health Tracking Service is not available."))
            return@callbackFlow
        }

        // --- Reset simulation state on new tracking session ---
        simulationTimeStep = 0.0
        simHr = 82.5f + (Random.nextFloat() - 0.5f) * 5

        trackerTypes.forEach { type ->
            if (activeTrackers[type] == null) {
                Log.d(TAG, "Attempting to initialize tracker: $type")
                try {
                    val tracker = healthTrackingService!!.getHealthTracker(type)
                    tracker.setEventListener(createEventListenerForType(type))
                    activeTrackers[type] = tracker
                    Log.i(TAG, "Successfully started tracker for $type")
                } catch (e: IllegalArgumentException) {
                    Log.e(
                        TAG,
                        "❌ FAILED: $type. Reason: ${e.message}. This is likely due to missing raw data permission/Developer Mode.",
                        e
                    )
                }
            }
        }

        // This part remains mostly the same, but the snapshot logic will now be different
        snapshotJob = coroutineScope.launch {
            while (true) {
                updateSimulatedValues()
                val realTimeData = latestRecord
                val snapshot = HealthDataRecord(
                    timestamp = Instant.now().toEpochMilli(),

                    // Use real data if available, otherwise use simulated data
                    hr = realTimeData.hr ?: simHr.toInt(),
                    ibi = realTimeData.ibi ?: generateSimulatedIbi(simHr),
                    ppgGreen = realTimeData.ppgGreen ?: (20000 + sin(simulationTimeStep) * 1000).toInt(),
                    ppgRed = realTimeData.ppgRed ?: (30000 + sin(simulationTimeStep) * 1200).toInt(),
                    ppgIr = realTimeData.ppgIr ?: (15000 + sin(simulationTimeStep) * 800).toInt(),
                    accX = realTimeData.accX ?: simAccX.toInt(),
                    accY = realTimeData.accY ?: simAccY.toInt(),
                    accZ = realTimeData.accZ ?: simAccZ.toInt(),
                    skinTemp = realTimeData.skinTemp ?: simSkinTemp,
                    eda = realTimeData.eda ?: simEda,

                    // Always provide the new simulated parameters
                    ecg = (sin(simulationTimeStep * 2) * 500 + (Random.nextFloat() - 0.5f) * 25).toFloat(),
                    spo2 = simSpo2,
                    bvp = (sin(simulationTimeStep) * 1500 + 32768).toFloat()
                )

                trySendBlocking(snapshot)
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }

        awaitClose {
            Log.i(TAG, "Flow is closing. Unsetting event listeners.")
            snapshotJob?.cancel()
            activeTrackers.values.forEach { it.unsetEventListener() }
            activeTrackers.clear()
        }
    }

    private fun updateSimulatedValues() {
        simulationTimeStep += 0.1
        // Heart Rate: Gentle, slow drift around a baseline
        simHr += (Random.nextFloat() - 0.5f) * 0.1f
        // Skin Temp: Very slow drift
        simSkinTemp += (Random.nextFloat() - 0.5f) * 0.005f
        // EDA: Slow random walk
        simEda += (Random.nextFloat() - 0.5f) * 0.01f
        if (simEda < 0.1f) simEda = 0.1f
        // SpO2: Stays high with tiny variations
        simSpo2 += (Random.nextFloat() - 0.5f) * 0.05f
        if (simSpo2 > 99.5f) simSpo2 = 99.5f
        if (simSpo2 < 97.0f) simSpo2 = 97.0f
        // Accelerometer: Simulates minor hand movements
        simAccX += (Random.nextFloat() - 0.5f) * 0.2f
        simAccZ += (Random.nextFloat() - 0.5f) * 0.2f
    }

    private fun generateSimulatedIbi(heartRate: Float): ArrayList<Int> {
        if (heartRate <= 0) return arrayListOf()
        val ibi = (60000 / heartRate).toInt()
        return arrayListOf(ibi + Random.nextInt(-5, 5))
    }

    private fun createEventListenerForType(trackerType: HealthTrackerType): HealthTracker.TrackerEventListener {
        return object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                for (dataPoint in dataPoints) {
                    when (trackerType) {
                        HealthTrackerType.PPG_CONTINUOUS -> {
                            latestRecord = latestRecord.copy(
                                ppgGreen = dataPoint.getValue(ValueKey.PpgSet.PPG_GREEN),
                                ppgIr = dataPoint.getValue(ValueKey.PpgSet.PPG_IR),
                                ppgRed = dataPoint.getValue(ValueKey.PpgSet.PPG_RED)
                            )
                        }
                        HealthTrackerType.HEART_RATE_CONTINUOUS -> {
                            val ibiList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                            latestRecord = latestRecord.copy(
                                hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE),
                                ibi = ibiList?.let { ArrayList(it) }
                            )
                        }
                        HealthTrackerType.ACCELEROMETER_CONTINUOUS -> {
                            latestRecord = latestRecord.copy(
                                accX = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X),
                                accY = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y),
                                accZ = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z)
                            )
                        }
                        HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS -> {
                            val temp = dataPoint.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                            latestRecord = latestRecord.copy(
                                skinTemp = temp
                            )
                        }
                        HealthTrackerType.EDA_CONTINUOUS -> {
                            latestRecord = latestRecord.copy(
                                eda = dataPoint.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE)
                            )
                        }
                        else -> { }
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