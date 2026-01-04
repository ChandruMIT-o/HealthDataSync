package com.samsung.health.hrdatatransfer.data.repository

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.data.ValueKey
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch
import com.samsung.health.hrdatatransfer.data.service.HealthTrackingServiceConnection
import com.samsung.health.hrdatatransfer.domain.repository.TrackingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TrackingRepoRaw"
// Threshold to trigger a batch send.
// ~60 seconds of data at 25Hz = 1500 samples.
// We use this to decide when to "flush" to the phone.
private const val BATCH_SIZE_LIMIT = 1500

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val healthTrackingServiceConnection: HealthTrackingServiceConnection
) : TrackingRepository {

    private val activeTrackers = mutableMapOf<HealthTrackerType, HealthTracker>()

    private val bufferLock = Any()

    // 1. Pre-allocate primitive arrays
    private val ppgGreenBuffer = FloatArray(BATCH_SIZE_LIMIT)
    private val ppgRedBuffer = FloatArray(BATCH_SIZE_LIMIT)
    private val ppgIrBuffer = FloatArray(BATCH_SIZE_LIMIT)

    private val accXBuffer = FloatArray(BATCH_SIZE_LIMIT * 2) // Accelerometer is often faster
    private val accYBuffer = FloatArray(BATCH_SIZE_LIMIT * 2)
    private val accZBuffer = FloatArray(BATCH_SIZE_LIMIT * 2)

    private val tempBuffer = FloatArray(100)

    // 2. Add indices to track position
    private var ppgIndex = 0
    private var accIndex = 0
    private var tempIndex = 0

    private var batchStartPpg: Long = 0L
    private var batchStartAcc: Long = 0L
    private var batchStartTemp: Long = 0L

    override fun stopTracking() {
        Log.i(TAG, "Stopping all raw trackers...")
        synchronized(bufferLock) {
            activeTrackers.values.forEach { it.unsetEventListener() }
            activeTrackers.clear()
            clearBuffers()
        }
    }

    override fun track(trackerTypes: Set<HealthTrackerType>): Flow<RawSensorBatch> = callbackFlow {
        val service = healthTrackingServiceConnection.getHealthTrackingService()
        if (service == null) {
            Log.e(TAG, "Samsung Health Service not connected. Cannot track.")
            close(IllegalStateException("Samsung Health Service unavailable"))
            return@callbackFlow
        }

        // Initialize timestamps for the first batch
        val now = Instant.now().toEpochMilli()
        batchStartPpg = now
        batchStartAcc = now
        batchStartTemp = now
        clearBuffers()

        Log.d(TAG, "Initializing trackers: $trackerTypes")

        trackerTypes.forEach { type ->
            try {
                // Special handling for PPG to ensure we get Red/IR/Green channels
                val tracker = if (type == HealthTrackerType.PPG_CONTINUOUS) {
                    service.getHealthTracker(type, setOf(PpgType.GREEN, PpgType.IR, PpgType.RED))
                } else {
                    service.getHealthTracker(type)
                }

                val listener = object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                        synchronized(bufferLock) {
                            // 1. Process incoming raw data
                            for (dp in dataPoints) {
                                processDataPoint(type, dp)
                            }

                            // 2. Check Batch Limit (Flow Control)
                            if (ppgIndex >= BATCH_SIZE_LIMIT) {
                                Log.d(TAG, "Buffer limit reached (${ppgGreenBuffer.size}). Emitting batch.")
                                val batch = createBatchAndReset()
                                val result = trySend(batch)
                                if (result.isFailure) {
                                    Log.w(TAG, "Failed to send batch downstream: ${result.exceptionOrNull()}")
                                }
                            }
                        }
                    }

                    override fun onFlushCompleted() {
                        // Optional: Handle manual flush completion if needed
                    }

                    override fun onError(e: HealthTracker.TrackerError) {
                        Log.e(TAG, "Tracker Runtime Error [${type}]: $e")
                        if (e == HealthTracker.TrackerError.PERMISSION_ERROR) {
                            close(SecurityException("Permission denied for sensor: $type"))
                        }
                    }
                }

                tracker.setEventListener(listener)
                activeTrackers[type] = tracker
                Log.i(TAG, "Started tracker: $type")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize tracker: $type", e)
                // We don't close the flow here; other sensors might still work.
            }
        }

        awaitClose {
            Log.d(TAG, "Flow closing. Cleaning up trackers.")
            stopTracking()
        }
    }

    override fun flushTrackers() {
        // In this architecture, we auto-flush based on size.
        // However, if the user hits "Stop", you might want to force send the partial buffer.
        // This is optional but recommended for ensuring no data loss at the end of a session.
        synchronized(bufferLock) {
            if (ppgGreenBuffer.isNotEmpty()) {
                Log.i(TAG, "Flushing remaining partial buffer...")
                // In a real flow, we'd need a way to emit this outside the callbackFlow scope,
                // or ensure the flow stays open long enough.
                // For now, we assume the auto-flush handles the bulk of data.
            }
        }
    }

    /**
     * Extracts raw values from the Samsung DataPoint and adds them to the correct buffer.
     */
    private fun processDataPoint(type: HealthTrackerType, dp: DataPoint) {
        // Initialize timestamps on first sample
        if (type == HealthTrackerType.PPG_CONTINUOUS && ppgIndex == 0) {
            batchStartPpg = System.currentTimeMillis()
        }
        if (type == HealthTrackerType.ACCELEROMETER_CONTINUOUS && accIndex == 0) {
            batchStartAcc = System.currentTimeMillis()
        }
        if (type == HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS && tempIndex == 0) {
            batchStartTemp = System.currentTimeMillis()
        }

        when (type) {
            HealthTrackerType.PPG_CONTINUOUS -> {
                if (ppgIndex < ppgGreenBuffer.size) {
                    ppgGreenBuffer[ppgIndex] = dp.getValue(ValueKey.PpgSet.PPG_GREEN).toFloat()
                    ppgRedBuffer[ppgIndex] = dp.getValue(ValueKey.PpgSet.PPG_RED).toFloat()
                    ppgIrBuffer[ppgIndex] = dp.getValue(ValueKey.PpgSet.PPG_IR).toFloat()
                    ppgIndex++
                }
            }
            HealthTrackerType.ACCELEROMETER_CONTINUOUS -> {
                if (accIndex < accXBuffer.size) {
                    accXBuffer[accIndex] = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X).toFloat()
                    accYBuffer[accIndex] = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y).toFloat()
                    accZBuffer[accIndex] = dp.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z).toFloat()
                    accIndex++
                }
            }
            HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS -> {
                if (tempIndex < tempBuffer.size) {
                    tempBuffer[tempIndex] = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    tempIndex++
                }
            }
            else -> {}
        }
    }
    /**
     * Packages current buffers into a RawSensorBatch, clears buffers, and resets timestamps.
     */
    private fun createBatchAndReset(): RawSensorBatch {
        val now = Instant.now().toEpochMilli()

        // copyOfRange creates a correctly sized array containing only the valid data
        val batch = RawSensorBatch(
            batchId = now,
            durationMs = (now - batchStartPpg).toInt(),
            ppgTimestamp = batchStartPpg,
            ppgGreen = ppgGreenBuffer.copyOfRange(0, ppgIndex),
            ppgRed = ppgRedBuffer.copyOfRange(0, ppgIndex),
            ppgIr = ppgIrBuffer.copyOfRange(0, ppgIndex),
            accTimestamp = batchStartAcc,
            accX = accXBuffer.copyOfRange(0, accIndex),
            accY = accYBuffer.copyOfRange(0, accIndex),
            accZ = accZBuffer.copyOfRange(0, accIndex),
            tempTimestamp = batchStartTemp,
            skinTemp = tempBuffer.copyOfRange(0, tempIndex)
        )

        clearBuffers()

        // Reset timestamps for next batch
        val nextStart = System.currentTimeMillis()
        batchStartPpg = nextStart
        batchStartAcc = nextStart
        batchStartTemp = nextStart

        return batch
    }

    private fun clearBuffers() {
        // We don't need to wipe the arrays, just reset the indices
        ppgIndex = 0
        accIndex = 0
        tempIndex = 0
    }
}