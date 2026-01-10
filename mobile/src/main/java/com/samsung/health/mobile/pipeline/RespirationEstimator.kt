package com.samsung.health.mobile.pipeline

object RespirationEstimator {

    fun estimate(ppg: FloatArray, peaks: List<Int>): Double? {
        if (peaks.size < 5) return null

        val ampSeries = peaks.map { ppg[it].toDouble() }
        val detrended = SignalProcessing.detrend(ampSeries.toDoubleArray())

        val (freq, power) = FastFFT.dominantFreq(
            detrended,
            fs = 25.0,
            low = 0.13,
            high = 0.40
        )

        

        val rr = freq * 60.0
        return if (rr in 8.0..30.0 && power > 0.25) rr else null
    }
}

