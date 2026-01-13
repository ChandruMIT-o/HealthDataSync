// --- mobile/src/main/java/com/samsung/health/mobile/pipeline/RespirationEstimator.kt ---
package com.samsung.health.mobile.pipeline

import kotlin.math.*

class RespirationEstimator {
    companion object {
        const val FS = 25.0

        // 1. Cardiac Band (0.7 - 3.0 Hz) - Reuse HR coeffs
        val B_CARD = HeartRateEstimator.B_HR
        val A_CARD = HeartRateEstimator.A_HR

        // 3. Respiration Filter Coefficients (0.13 - 0.40 Hz @ 4Hz)
        val B_RESP = doubleArrayOf(0.0065084712444029, 0.0000000000000000, -0.0195254137332086, 0.0000000000000000, 0.0195254137332086, 0.0000000000000000, -0.0065084712444029)
        val A_RESP = doubleArrayOf(1.0000000000000000, -4.8251185514844011, 9.9909561999058631, -11.3684296658347943, 7.5019077262344833, -2.7232426523449411, 0.4253228610612525)
    }

    fun estimate(ppg: DoubleArray): Pair<Double, Double> {
        // 1. Cardiac Bandpass
        val cardiac = DspUtils.filtfilt(B_CARD, A_CARD, ppg)

        // 2. Beat Detection
        val std = cardiac.standardDeviation()
        val peaks = DspUtils.findPeaks(cardiac, (0.4 * FS).toInt(), std * 0.4)

        if (peaks.size < 6) return Pair(Double.NaN, 0.0)

        // 3. RIIV (Amplitudes at peaks)
        val tBeats = peaks.map { it / FS }.toDoubleArray()
        val riiv = peaks.map { cardiac[it] }.toDoubleArray()

        // 4. Interpolate to Uniform Grid (4 Hz)
        val fsInterp = 4.0
        val duration = tBeats.last() - tBeats.first()
        val numSamples = (duration * fsInterp).toInt()

        if (numSamples <= 0) return Pair(Double.NaN, 0.0)

        val tUniform = DoubleArray(numSamples) { tBeats.first() + it / fsInterp }
        val riivInterp = DspUtils.interp(tUniform, tBeats, riiv)

        // 5. Resp Bandpass (0.13 - 0.40 Hz)
        val respSignal = DspUtils.filtfilt(B_RESP, A_RESP, riivInterp)

        // 6. PSD / Welch
        val (freqs, psd) = DspUtils.welchPsd(respSignal, fsInterp)

        // Find peak in 0.13-0.40 range
        var maxPower = -1.0
        var peakFreq = 0.0
        var sumPower = 0.0

        for (i in freqs.indices) {
            val f = freqs[i]
            if (f in 0.13..0.40) {
                sumPower += psd[i]
                if (psd[i] > maxPower) {
                    maxPower = psd[i]
                    peakFreq = f
                }
            }
        }

        if (maxPower == -1.0) return Pair(Double.NaN, 0.0)

        val rrBpm = peakFreq * 60.0
        val confidence = maxPower / (sumPower + 1e-8)

        return Pair(rrBpm, confidence)
    }

    private fun DoubleArray.standardDeviation(): Double {
        val avg = this.average()
        return sqrt(this.map { (it - avg).pow(2) }.average())
    }
}