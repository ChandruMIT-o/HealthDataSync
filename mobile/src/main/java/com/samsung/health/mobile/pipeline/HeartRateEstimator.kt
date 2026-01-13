// --- mobile/src/main/java/com/samsung/health/mobile/pipeline/HeartRateEstimator.kt ---
package com.samsung.health.mobile.pipeline

import kotlin.math.abs
// Removed invalid import: import kotlin.math.mean

class HeartRateEstimator {

    companion object {
        const val FS = 25.0

        // 1. HR & Cardiac Filter Coefficients (0.7 - 3.0 Hz)
        // Generated from Python scipy.signal.butter(3, ...)
        val B_HR = doubleArrayOf(0.0146240223926464, 0.0000000000000000, -0.0438720671779392, 0.0000000000000000, 0.0438720671779392, 0.0000000000000000, -0.0146240223926464)
        val A_HR = doubleArrayOf(1.0000000000000000, -4.5294678805556261, 8.8121241219607693, -9.4658031362572430, 5.9370617695819750, -2.0617195343077448, 0.3091719210559029)
    }

    fun estimate(ppgGreen: DoubleArray): Double {
        // 1. Bandpass
        val filtered = DspUtils.filtfilt(B_HR, A_HR, ppgGreen)

        // 2. Peak Detection
        val std = filtered.standardDeviation()
        val distance = (0.4 * FS).toInt() // max 150 bpm
        val prominence = std * 0.5

        val peaks = DspUtils.findPeaks(filtered, distance, prominence)

        if (peaks.size < 2) return Double.NaN

        // 3. Calculate RR
        val rrSeconds = ArrayList<Double>()
        for (i in 1 until peaks.size) {
            rrSeconds.add((peaks[i] - peaks[i - 1]) / FS)
        }

        val meanRr = rrSeconds.average() // Kotlin uses .average()
        if (meanRr <= 0) return Double.NaN

        return 60.0 / meanRr
    }

    private fun DoubleArray.standardDeviation(): Double {
        val avg = this.average()
        var sum = 0.0
        for (v in this) sum += (v - avg) * (v - avg)
        return kotlin.math.sqrt(sum / this.size)
    }
}