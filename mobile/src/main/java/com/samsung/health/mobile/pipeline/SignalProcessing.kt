package com.samsung.health.mobile.pipeline

import com.samsung.health.mobile.RawSensorBatch
import java.util.ArrayDeque
import kotlin.math.pow
import kotlin.math.sqrt

data class PipelineResult(
    val timestamp: Long,
    val hr: Double,
    val spo2: Double,
    val rr: Double,
    val rrConfidence: Double,
    val ibi: List<Double>,
    val movement: Double,
    val skinTemperature: Double
)

class SignalProcessing {
    private val hrEstimator = HeartRateEstimator()
    private val spo2Estimator = SpO2Estimator()
    private val rrEstimator = RespirationEstimator()
    private val ibiEstimator = IBIEstimator()

    private val MAX_BUFFER_SIZE = (25 * 30 * 1.5).toInt()

    // Buffers
    private val bufferGreen = ArrayDeque<Double>()
    private val bufferRed = ArrayDeque<Double>()
    private val bufferIr = ArrayDeque<Double>()
    private val bufferAccX = ArrayDeque<Double>()
    private val bufferAccY = ArrayDeque<Double>()
    private val bufferAccZ = ArrayDeque<Double>()
    private val bufferTemp = ArrayDeque<Double>()
    private val bufferTimestamp = ArrayDeque<Long>()

    private var samplesSinceLastOutput = 0
    private val STEP_SAMPLES = 25

    // Track last known temp for Zero-Order Hold interpolation
    private var lastKnownTemp = 0.0

    fun processBatch(batch: RawSensorBatch): List<PipelineResult> {
        val results = ArrayList<PipelineResult>()
        val n = batch.ppgGreen.size

        // Array sizes might differ due to sampling rates
        val nAcc = batch.accX.size
        val nTemp = batch.skinTemp.size

        val stepMs = 40L

        for (i in 0 until n) {
            val ts = batch.ppgTimestamp + (i * stepMs)

            bufferGreen.add(batch.ppgGreen[i].toDouble())
            bufferRed.add(batch.ppgRed[i].toDouble())
            bufferIr.add(batch.ppgIr[i].toDouble())

            // Buffer ACC (Zero pad if missing, usually fine for movement)
            if (i < nAcc) {
                bufferAccX.add(batch.accX[i].toDouble())
                bufferAccY.add(batch.accY[i].toDouble())
                bufferAccZ.add(batch.accZ[i].toDouble())
            } else {
                bufferAccX.add(0.0); bufferAccY.add(0.0); bufferAccZ.add(0.0)
            }

            // Buffer TEMP (Zero-Order Hold) -- FIX FOR 0.4 ISSUE
            if (i < nTemp) {
                lastKnownTemp = batch.skinTemp[i].toDouble()
            }
            // Always add the last known temp (repeat it) instead of adding 0.0
            bufferTemp.add(lastKnownTemp)

            bufferTimestamp.add(ts)

            if (bufferGreen.size > MAX_BUFFER_SIZE) {
                bufferGreen.removeFirst()
                bufferRed.removeFirst()
                bufferIr.removeFirst()
                bufferAccX.removeFirst()
                bufferAccY.removeFirst()
                bufferAccZ.removeFirst()
                bufferTemp.removeFirst()
                bufferTimestamp.removeFirst()
            }

            samplesSinceLastOutput++

            if (samplesSinceLastOutput >= STEP_SAMPLES && bufferGreen.size >= 250) {
                val window10s = getTail(250)
                val windowStartTs = window10s.timestamps.first()

                val hr = hrEstimator.estimate(window10s.green)
                val spo2 = spo2Estimator.estimate(window10s.red, window10s.ir)
                val ibiRes = ibiEstimator.estimate(window10s.green)

                var rr = Double.NaN
                var rrConf = 0.0
                if (bufferGreen.size >= 750) {
                    val window30s = getTail(750)
                    val rrRes = rrEstimator.estimate(window30s.green)
                    rr = rrRes.first
                    rrConf = rrRes.second
                }

                val movement = calculateMovement(window10s.accX, window10s.accY, window10s.accZ)

                // Now averaging the repeated values gives the correct temperature
                val tempAvg = window10s.temp.average()

                results.add(
                    PipelineResult(
                        timestamp = windowStartTs,
                        hr = hr,
                        spo2 = spo2,
                        rr = rr,
                        rrConfidence = rrConf,
                        ibi = ibiRes.ibiMs,
                        movement = movement,
                        skinTemperature = tempAvg
                    )
                )
                samplesSinceLastOutput = 0
            }
        }
        return results
    }

    private fun calculateMovement(x: DoubleArray, y: DoubleArray, z: DoubleArray): Double {
        if (x.isEmpty()) return 0.0
        val magnitudes = DoubleArray(x.size)
        for (i in x.indices) magnitudes[i] = sqrt(x[i]*x[i] + y[i]*y[i] + z[i]*z[i])
        val avg = magnitudes.average()
        var sumSq = 0.0
        for (m in magnitudes) sumSq += (m - avg).pow(2)
        return sqrt(sumSq / magnitudes.size)
    }

    data class SensorWindow(
        val green: DoubleArray, val red: DoubleArray, val ir: DoubleArray,
        val accX: DoubleArray, val accY: DoubleArray, val accZ: DoubleArray,
        val temp: DoubleArray,
        val timestamps: List<Long>
    )

    private fun getTail(samples: Int): SensorWindow {
        val green = DoubleArray(samples); val red = DoubleArray(samples); val ir = DoubleArray(samples)
        val ax = DoubleArray(samples); val ay = DoubleArray(samples); val az = DoubleArray(samples)
        val temp = DoubleArray(samples)
        val ts = LongArray(samples)

        val itG = bufferGreen.descendingIterator(); val itR = bufferRed.descendingIterator(); val itI = bufferIr.descendingIterator()
        val itX = bufferAccX.descendingIterator(); val itY = bufferAccY.descendingIterator(); val itZ = bufferAccZ.descendingIterator()
        val itTmp = bufferTemp.descendingIterator()
        val itT = bufferTimestamp.descendingIterator()

        for (i in samples - 1 downTo 0) {
            if (itG.hasNext()) green[i] = itG.next()
            if (itR.hasNext()) red[i] = itR.next()
            if (itI.hasNext()) ir[i] = itI.next()
            if (itX.hasNext()) ax[i] = itX.next()
            if (itY.hasNext()) ay[i] = itY.next()
            if (itZ.hasNext()) az[i] = itZ.next()
            if (itTmp.hasNext()) temp[i] = itTmp.next()
            if (itT.hasNext()) ts[i] = itT.next()
        }

        return SensorWindow(green, red, ir, ax, ay, az, temp, ts.toList())
    }
}