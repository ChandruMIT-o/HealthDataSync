package com.samsung.health.mobile.pipeline

import kotlin.math.*

object SignalProcessing {

    private const val FS = 25.0

    /* -------------------- BUTTERWORTH (2nd order IIR) -------------------- */

    fun butterBandpass(
        signal: DoubleArray,
        lowHz: Double,
        highHz: Double
    ): DoubleArray {
        val filtered = iirFilter(signal, lowHz, highHz)
        return filtfilt(filtered, lowHz, highHz)
    }

    private fun iirFilter(
        x: DoubleArray,
        low: Double,
        high: Double
    ): DoubleArray {
        val y = DoubleArray(x.size)

        val w1 = 2 * PI * low / FS
        val w2 = 2 * PI * high / FS
        val bw = w2 - w1
        val wc = sqrt(w1 * w2)

        val a0 = 1 + bw
        val a1 = -2 * cos(wc)
        val a2 = 1 - bw
        val b0 = bw
        val b1 = 0.0
        val b2 = -bw

        for (i in 2 until x.size) {
            y[i] = (b0 * x[i] + b1 * x[i - 1] + b2 * x[i - 2]
                    - a1 * y[i - 1] - a2 * y[i - 2]) / a0
        }
        return y
    }

    /** Forward–backward filtering = zero phase */
    private fun filtfilt(
        signal: DoubleArray,
        low: Double,
        high: Double
    ): DoubleArray {
        val forward = iirFilter(signal, low, high)
        val reversed = forward.reversedArray()
        return iirFilter(reversed, low, high).reversedArray()
    }

    /* -------------------- DETREND -------------------- */

    fun detrend(signal: DoubleArray): DoubleArray {
        val n = signal.size
        val xMean = (n - 1) / 2.0
        val yMean = signal.average()

        var num = 0.0
        var den = 0.0
        for (i in signal.indices) {
            num += (i - xMean) * (signal[i] - yMean)
            den += (i - xMean).pow(2)
        }

        val slope = num / den
        val intercept = yMean - slope * xMean

        return DoubleArray(n) { i ->
            signal[i] - (slope * i + intercept)
        }
    }

    /* -------------------- PEAK DETECTION -------------------- */

    fun findPeaks(
        signal: DoubleArray,
        minDistance: Int
    ): List<Int> {
        val peaks = mutableListOf<Int>()
        var lastPeak = -minDistance

        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] &&
                signal[i] > signal[i + 1] &&
                i - lastPeak >= minDistance
            ) {
                peaks.add(i)
                lastPeak = i
            }
        }
        return peaks
    }

    /* -------------------- FFT POWER SPECTRUM -------------------- */

    fun dominantFrequency(
        signal: DoubleArray,
        lowHz: Double,
        highHz: Double
    ): Pair<Double, Double> {
        val n = signal.size
        val re = DoubleArray(n)
        val im = DoubleArray(n)

        for (k in 0 until n) {
            for (t in 0 until n) {
                val angle = 2 * PI * t * k / n
                re[k] += signal[t] * cos(angle)
                im[k] -= signal[t] * sin(angle)
            }
        }

        var maxPower = 0.0
        var freq = 0.0

        for (k in 1 until n / 2) {
            val f = k * FS / n
            if (f in lowHz..highHz) {
                val power = re[k].pow(2) + im[k].pow(2)
                if (power > maxPower) {
                    maxPower = power
                    freq = f
                }
            }
        }
        return freq to maxPower
    }
}

