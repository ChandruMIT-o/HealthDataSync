package com.samsung.health.mobile.pipeline

object IBIEstimator {

    fun estimate(peaks: List<Int>): List<Long> {
        val ibi = peaks.zipWithNext { a, b ->
            ((b - a) * 1000L) / 25L
        }

        return ibi.filter { it in 300L..2000L }
    }
}

