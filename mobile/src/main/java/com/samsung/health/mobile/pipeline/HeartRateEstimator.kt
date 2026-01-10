package com.samsung.health.mobile.pipeline

object HeartRateEstimator {

    fun estimate(ppg: FloatArray): Pair<Double?, List<Int>> {
        val signal = ppg.map { it.toDouble() }.toDoubleArray()

        val filtered = SignalProcessing.butterBandpass(signal, 0.7, 3.0)
        val peaks = SignalProcessing.findPeaks(filtered, minDistance = 16)

        if (peaks.size < 2) return null to emptyList()

        val rr = peaks.zipWithNext { a, b -> (b - a) / 25.0 }
        val meanRR = rr.average()

        val hr = 60.0 / meanRR
        if (hr !in 40.0..180.0) return null to peaks

        return hr to peaks
    }
}

