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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

// --- IMPORTS FOR PROCESSING ---
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.TransformType
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.sqrt


private const val TAG = "TrackingRepositoryImpl"
private const val SNAPSHOT_INTERVAL_MS = 1000L // 1 Hz
private const val DATA_STALE_MS = 60_000L // 1 minute

// --- CONSTANTS FOR PROCESSING ---
private const val PROCESSING_INTERVAL_MS = 5000L // Run heavy processing every 5 seconds
private const val PPG_FS = 25.0 // Assuming 25Hz for PPG sensors
private const val PPG_WINDOW_SECONDS = 5
private const val PPG_WINDOW_SAMPLES = (PPG_WINDOW_SECONDS * PPG_FS).toInt() // 5 seconds * 25Hz = 125 samples
private const val HR_WINDOW_SECONDS = 120 // 120 seconds for respiration rate
private const val MIN_HR_SAMPLES_FOR_RESP = 30 // Need at least 30s of HR data
private const val MIN_PPG_SAMPLES_FOR_SPO2 = (PPG_WINDOW_SAMPLES * 0.8).toInt() // Need 80% of window

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val healthTrackingServiceConnection: HealthTrackingServiceConnection,
    @ApplicationContext private val context: Context,
) : TrackingRepository {

    private var healthTrackingService: HealthTrackingService? = null
    private val activeTrackers = mutableMapOf<HealthTrackerType, HealthTracker>()
    private val latestRecordFlow = MutableStateFlow(HealthDataRecord())

    @Volatile
    private var lastSensorUpdateTimestamps = mutableMapOf<String, Long>()

    private var simEda = 0.8f
    private var simulationTimeStep = 0.0

    // --- Caches for derived features ---
    @Volatile private var cachedRespirationRate: Double? = null
    @Volatile private var cachedPpgResult = PpgResult(null, null)

    // --- Data Buffers ---
    private val ppgGreenBuffer = ConcurrentLinkedDeque<Double>()
    private val ppgRedBuffer = ConcurrentLinkedDeque<Double>()
    private val ppgIrBuffer = ConcurrentLinkedDeque<Double>()
    private val hrBuffer = ConcurrentLinkedDeque<Pair<Long, Double>>() // <Timestamp, HR>

    // --- Lock for PPG buffer race condition ---
    private val ppgLock = Any()


    override fun stopTracking() {
        Log.i(TAG, "Stopping all trackers.")
        activeTrackers.values.forEach { it.unsetEventListener() }
        activeTrackers.clear()
        latestRecordFlow.value = HealthDataRecord()
        lastSensorUpdateTimestamps.clear()

        // --- Clear buffers ---
        ppgGreenBuffer.clear()
        ppgRedBuffer.clear()
        ppgIrBuffer.clear()
        hrBuffer.clear()
    }

    override fun track(trackerTypes: Set<HealthTrackerType>): Flow<HealthDataRecord> = callbackFlow {
        healthTrackingService = healthTrackingServiceConnection.getHealthTrackingService()
        if (healthTrackingService == null) {
            Log.e(TAG, "Health Tracking Service is not available. Cannot start tracking.")
            close(IllegalStateException("Health Tracking Service is not available."))
            return@callbackFlow
        }

        // --- Clear buffers on new track ---
        latestRecordFlow.value = HealthDataRecord()
        lastSensorUpdateTimestamps.clear()
        ppgGreenBuffer.clear()
        ppgRedBuffer.clear()
        ppgIrBuffer.clear()
        hrBuffer.clear()
        cachedRespirationRate = null
        cachedPpgResult = PpgResult(null, null)

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

        // --- Slower loop for heavy processing (FIX for Watch Freeze) ---
        val processingLoop = launch(Dispatchers.Default) {
            while (isActive) {
                // Wait *before* the next calculation
                delay(PROCESSING_INTERVAL_MS)
                Log.d(TAG, "Running heavy processing...")
                cachedRespirationRate = processHrWindow()
                cachedPpgResult = processPpgWindow()
                Log.d(TAG, "Heavy processing complete.")
            }
        }

        fun generateECGSample(time: Double, heartRate: Int? = 72): Float {
            // One heartbeat period in seconds
            val period = 60f / (heartRate ?: 72)
            val t = (time % period) / period  // normalize to 0..1 within each beat

            // Base noise
            var ecg = (Random.nextFloat() - 0.5f) * 10f

            // P-wave (~0.1s)
            if (t in 0.1f..0.18f) {
                ecg += 150f * sin(((t - 0.14f) / 0.04f) * PI).toFloat()
            }

            // Q dip (~0.25s)
            if (t in 0.25f..0.27f) {
                ecg -= 400f * exp(-((t - 0.26f).pow(2)) / 0.0001f).toFloat()
            }

            // R spike (~0.3s)
            if (t in 0.28f..0.31f) {
                ecg += 1000f * exp(-((t - 0.295f).pow(2)) / 0.00002f).toFloat()
            }

            // S dip (~0.33s)
            if (t in 0.32f..0.35f) {
                ecg -= 300f * exp(-((t - 0.34f).pow(2)) / 0.00005f).toFloat()
            }

            // T-wave (~0.45s)
            if (t in 0.45f..0.6f) {
                ecg += 250f * sin(((t - 0.45f) / 0.15f) * PI).toFloat()
            }

            return ecg
        }

        // --- Fast loop for sending data ---
        val snapshotLoop = launch(Dispatchers.Default) {
            while (isActive) {
                // 1. Get the latest "instant" values (HR, ACC, etc.)
                val realTimeData = latestRecordFlow.value
                val now = Instant.now().toEpochMilli()

                // 2. Get the latest *cached* results from the slow loop
                val ppgResult = cachedPpgResult
                val respirationRate = cachedRespirationRate

                if (realTimeData.hr != null && realTimeData.hr == 0) {
                    Log.w(TAG, "Off-wrist detected (Real HR == 0). Skipping send.")
                    delay(SNAPSHOT_INTERVAL_MS)
                    continue
                }

                updateSimulatedValues()

                val hr = getStaleData(ValueKey.HeartRateSet.HEART_RATE.toString(), now, realTimeData.hr, 0)

                // 3. Create snapshot, now including cached derived data
                val snapshot = HealthDataRecord(
                    timestamp = now,
                    hr = hr,
                    ibi = getStaleData(ValueKey.HeartRateSet.IBI_LIST.toString(), now, realTimeData.ibi, null),
                    ppgGreen = getStaleData(ValueKey.PpgSet.PPG_GREEN.toString(), now, realTimeData.ppgGreen, 0),
                    ppgRed = getStaleData(ValueKey.PpgSet.PPG_RED.toString(), now, realTimeData.ppgRed, 0),
                    ppgIr = getStaleData(ValueKey.PpgSet.PPG_IR.toString(), now, realTimeData.ppgIr, 0),
                    accX = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_X.toString(), now, realTimeData.accX, 0),
                    accY = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_Y.toString(), now, realTimeData.accY, 0),
                    accZ = getStaleData(ValueKey.AccelerometerSet.ACCELEROMETER_Z.toString(), now, realTimeData.accZ, 0),
                    skinTemp = getStaleData(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE.toString(), now, realTimeData.skinTemp, 0.0f),
                    eda = realTimeData.eda ?: simEda,
                    ecg = realTimeData.ecg ?: generateECGSample(simulationTimeStep, hr),

                    // 4. Add new derived values from the cache
                    bvp = ppgResult.bvp,
                    spo2 = ppgResult.spo2,
                    respirationRate = respirationRate
                )
                trySendBlocking(snapshot)
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
        awaitClose {
            Log.i(TAG, "Flow is closing. Cancelling loops and unsetting listeners.")
            // Cancel BOTH loops
            processingLoop.cancel()
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
                val now = Instant.now().toEpochMilli()
                for (dataPoint in dataPoints) {
                    try {
                        when (trackerType) {
                            HealthTrackerType.PPG_CONTINUOUS -> {
                                val green = dataPoint.getValue(ValueKey.PpgSet.PPG_GREEN).toDouble()
                                val ir = dataPoint.getValue(ValueKey.PpgSet.PPG_IR).toDouble()
                                val red = dataPoint.getValue(ValueKey.PpgSet.PPG_RED).toDouble()

                                // --- FIX for Race Condition ---
                                synchronized(ppgLock) {
                                    ppgGreenBuffer.add(green)
                                    ppgIrBuffer.add(ir)
                                    ppgRedBuffer.add(red)

                                    // Prune buffers
                                    while (ppgGreenBuffer.size > PPG_WINDOW_SAMPLES) ppgGreenBuffer.poll()
                                    while (ppgIrBuffer.size > PPG_WINDOW_SAMPLES) ppgIrBuffer.poll()
                                    while (ppgRedBuffer.size > PPG_WINDOW_SAMPLES) ppgRedBuffer.poll()
                                }
                                // --- END FIX ---

                                latestRecordFlow.update {
                                    it.copy(
                                        ppgGreen = green.toInt(),
                                        ppgIr = ir.toInt(),
                                        ppgRed = red.toInt()
                                    )
                                }
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_GREEN.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_IR.toString()] = now
                                lastSensorUpdateTimestamps[ValueKey.PpgSet.PPG_RED.toString()] = now
                            }
                            HealthTrackerType.HEART_RATE_CONTINUOUS -> {
                                val ibiList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
                                val hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)

                                // Add to buffer for resp rate
                                hrBuffer.add(Pair(now, hr.toDouble()))
                                while (hrBuffer.size > HR_WINDOW_SECONDS) hrBuffer.poll()

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

    // =======================================================
    // --- SIGNAL PROCESSING FUNCTIONS ---
    // =======================================================

    /**
     * Data class to hold the results of PPG processing.
     */
    private data class PpgResult(val bvp: Float? = null, val spo2: Float? = null)

    /**
     * Processes the current PPG window to get BVP and SpO2.
     */
    private fun processPpgWindow(): PpgResult {
        if (ppgGreenBuffer.size < MIN_PPG_SAMPLES_FOR_SPO2) {
            return PpgResult(null, null) // Not enough data
        }

        // --- FIX for Race Condition ---
        val green: DoubleArray
        val red: DoubleArray
        val ir: DoubleArray

        synchronized(ppgLock) {
            // This is now an atomic operation.
            green = ppgGreenBuffer.toDoubleArray()
            red = ppgRedBuffer.toDoubleArray()
            ir = ppgIrBuffer.toDoubleArray()
        }
        // --- END FIX ---


        // 1. Calculate BVP
        val bvpSignal = detrend(green)
        val latestBvp = bvpSignal.lastOrNull()?.toFloat()

        // 2. Calculate SpO2
        val spo2 = calculateSpo2(red, ir)

        return PpgResult(bvp = latestBvp, spo2 = spo2)
    }

    /**
     * Calculates SpO2 from Red and IR signals using the "Ratio of Ratios" method.
     */
    private fun calculateSpo2(red: DoubleArray, ir: DoubleArray): Float? {
        try {
            // Detrend to get AC component
            val redAC = detrend(red)
            val irAC = detrend(ir)

            // Get Root Mean Square (RMS) of AC
            val redRms = rms(redAC)
            val irRms = rms(irAC)

            // Get average for DC component
            val redDC = red.average()
            val irDC = ir.average()

            if (redDC == 0.0 || irDC == 0.0 || irRms == 0.0) {
                return null // Avoid division by zero
            }

            val R = (redRms / redDC) / (irRms / irDC)

            // Standard formula: SpO2 = 110 - 25 * R
            val spo2 = (110.0 - 25.0 * R).coerceIn(0.0, 100.0)

            if (spo2.isNaN()) return null
            return spo2.toFloat()

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SpO2", e)
            return null
        }
    }

    /**
     * Processes the current HR window to get Respiration Rate.
     */
    private fun processHrWindow(): Double? {
        if (hrBuffer.size < MIN_HR_SAMPLES_FOR_RESP) {
            return null // Not enough data
        }

        try {
            // 1. Get HR data and detrend it
            val hrData = hrBuffer.map { it.second }.toDoubleArray()
            val detrendedHr = detrend(hrData)
            val n = detrendedHr.size
            if (n < MIN_HR_SAMPLES_FOR_RESP) return null

            // --- FIX for FFT Power of 2 ---
            // Find the next power of 2 >= n (e.g., if n=120, paddedN=128)
            val paddedN = 1 shl (32 - Integer.numberOfLeadingZeros(n - 1))
            val paddedData = DoubleArray(paddedN)
            // Copy our detrended data into the new, larger array
            System.arraycopy(detrendedHr, 0, paddedData, 0, n)
            // --- END FIX ---

            // 3. Perform FFT on the *padded* data
            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            val fftResult = transformer.transform(paddedData, TransformType.FORWARD) // Use paddedData

            // 4. Find peak frequency in the valid range (0.1 Hz to 0.5 Hz)
            val fs = 1.0 // Our HR data is 1 Hz

            // Use paddedN for frequency calculation
            val freqs = (0 until paddedN / 2).map { it * fs / paddedN }

            var maxMagnitude = -1.0
            var peakFreq = 0.0

            val minRespFreq = 0.1 // 0.1 Hz = 6 breaths/min
            val maxRespFreq = 0.5 // 0.5 Hz = 30 breaths/min

            // Iterate up to paddedN / 2
            for (i in 1 until paddedN / 2) { // Start at 1 to skip DC component
                val freq = freqs[i]
                if (freq >= minRespFreq && freq <= maxRespFreq) {
                    val magnitude = fftResult[i].abs() // Get magnitude
                    if (magnitude > maxMagnitude) {
                        maxMagnitude = magnitude
                        peakFreq = freq
                    }
                }
            }

            if (maxMagnitude == -1.0) {
                return null // No peak found in range
            }

            // 5. Convert frequency (Hz) to Breaths Per Minute
            return peakFreq * 60.0

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating Respiration Rate", e)
            return null
        }
    }

    /**
     * Detrends a signal using linear regression.
     */
    private fun detrend(data: DoubleArray): DoubleArray {
        val regression = SimpleRegression()
        data.forEachIndexed { index, value ->
            regression.addData(index.toDouble(), value)
        }

        val slope = regression.slope
        val intercept = regression.intercept

        if (slope.isNaN() || intercept.isNaN()) {
            return data // Not enough data
        }

        return data.mapIndexed { index, value ->
            val trend = (slope * index) + intercept
            value - trend
        }.toDoubleArray()
    }

    /**
     * Calculates the Root Mean Square (RMS) of a signal.
     */
    private fun rms(data: DoubleArray): Double {
        if (data.isEmpty()) return 0.0
        return sqrt(data.sumOf { it * it } / data.size)
    }
}