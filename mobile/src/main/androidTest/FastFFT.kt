package com.samsung.health.mobile.pipeline

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.pow

object FastFFT {

    fun dominantFreq(
        signal: DoubleArray,
        fs: Double,
        low: Double,
        high: Double
    ): Pair<Double, Double> {

        val n = signal.size
        val fftData = DoubleArray(n * 2)

        for (i in signal.indices) {
            fftData[2 * i] = signal[i]
            fftData[2 * i + 1] = 0.0
        }

        val fft = DoubleFFT_1D(n.toLong())
        fft.complexForward(fftData)

        var maxPower = 0.0
        var freq = 0.0

        for (k in 1 until n / 2) {
            val f = k * fs / n
            if (f in low..high) {
                val re = fftData[2 * k]
                val im = fftData[2 * k + 1]
                val power = re.pow(2) + im.pow(2)

                if (power > maxPower) {
                    maxPower = power
                    freq = f
                }
            }
        }
        return freq to maxPower
    }
}

