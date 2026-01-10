package com.samsung.health.mobile.pipeline

object ValidationUtils {

    fun confidence(
        hrOk: Boolean,
        ibiCoverage: Double,
        rrOk: Boolean
    ): Double {
        var score = 0.0
        if (hrOk) score += 0.4
        score += ibiCoverage * 0.4
        if (rrOk) score += 0.2
        return score.coerceIn(0.0, 1.0)
    }
}

