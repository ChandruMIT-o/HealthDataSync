// --- mobile/src/main/java/com/samsung/health/mobile/pipeline/IBIEstimator.kt ---
package com.samsung.health.mobile.pipeline

import kotlin.math.*

class IBIEstimator {
    companion object {
        const val FS = 25.0
        val B_BVP = HeartRateEstimator.B_HR
        val A_BVP = HeartRateEstimator.A_HR
    }

    data class Result(val ibiMs: List<Double>, val timestamps: List<Double>)

    fun estimate(ppg: DoubleArray): Result {
        // 1. Detrend (Linear)
        val detrended = detrend(ppg)

        // 2. Bandpass
        val bvp = DspUtils.filtfilt(B_BVP, A_BVP, detrended)

        // 3. Normalize
        val std = bvp.standardDeviation()
        val bvpNorm = bvp.map { (it - bvp.average()) / (std + 1e-8) }.toDoubleArray()

        // 4. Peaks
        val peaks = DspUtils.findPeaks(bvpNorm, (0.4 * FS).toInt(), 0.5) // Prominence 0.5 on normalized data

        if (peaks.size < 2) return Result(emptyList(), emptyList())

        val beatTimes = peaks.map { it / FS }
        val ibiMs = ArrayList<Double>()
        val validBeatTimes = ArrayList<Double>()

        for (i in 1 until beatTimes.size) {
            val interval = (beatTimes[i] - beatTimes[i-1]) * 1000.0
            if (interval in 300.0..2000.0) {
                ibiMs.add(interval)
                validBeatTimes.add(beatTimes[i]) // Timestamp of the beat
            }
        }

        return Result(ibiMs, validBeatTimes)
    }

    private fun detrend(y: DoubleArray): DoubleArray {
        val n = y.size
        if (n < 2) return y
        val x = DoubleArray(n) { it.toDouble() }

        // Simple linear regression to find slope/intercept
        val xMean = x.average()
        val yMean = y.average()

        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (x[i] - xMean) * (y[i] - yMean)
            den += (x[i] - xMean).pow(2)
        }
        val m = num / den
        val c = yMean - m * xMean

        return DoubleArray(n) { i -> y[i] - (m * i + c) }
    }

    private fun DoubleArray.standardDeviation(): Double {
        val avg = this.average()
        return sqrt(this.map { (it - avg).pow(2) }.average())
    }
}