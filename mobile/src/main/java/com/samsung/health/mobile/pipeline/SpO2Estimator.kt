// --- mobile/src/main/java/com/samsung/health/mobile/pipeline/SpO2Estimator.kt ---
package com.samsung.health.mobile.pipeline

import kotlin.math.*

class SpO2Estimator {

    companion object {
        const val FS = 25.0

        // 2. SpO2 Filter Coefficients (0.5 - 5.0 Hz)
        val B_SPO2 = doubleArrayOf(0.0767459069023136, 0.0000000000000000, -0.2302377207069409, 0.0000000000000000, 0.2302377207069409, 0.0000000000000000, -0.0767459069023136)
        val A_SPO2 = doubleArrayOf(1.0000000000000000, -3.4767608600037740, 5.0801848641096230, -4.2310052826910205, 2.2392861745041364, -0.6943733767743361, 0.0842735738496220)
    }

    // Dynamic Baseline (Running Median)
    private val rorHistory = ArrayDeque<Double>()

    fun estimate(ppgRed: DoubleArray, ppgIr: DoubleArray): Double {
        // 1. Quality Gate
        if (!qualityGate(ppgRed, ppgIr)) return Double.NaN

        // 2. Compute RoR
        val r = computeRor(ppgRed, ppgIr)
        if (r.isNaN()) return Double.NaN

        // 3. Update Baseline (Approximate median of history)
        rorHistory.add(r)
        if (rorHistory.size > 100) rorHistory.removeFirst() // Keep last ~100 valid epochs
        val r0 = rorHistory.sorted()[rorHistory.size / 2]

        // 4. Map to SpO2
        return rorToSpo2(r, r0)
    }

    private fun computeRor(red: DoubleArray, ir: DoubleArray): Double {
        val redAc = DspUtils.filtfilt(B_SPO2, A_SPO2, red)
        val irAc = DspUtils.filtfilt(B_SPO2, A_SPO2, ir)

        val redDc = red.average()
        val irDc = ir.average()

        if (redDc <= 0 || irDc <= 0) return Double.NaN

        val redAcRms = sqrt(redAc.map { it * it }.average())
        val irAcRms = sqrt(irAc.map { it * it }.average())

        if (irAcRms == 0.0) return Double.NaN

        return (redAcRms / redDc) / (irAcRms / irDc)
    }

    private fun qualityGate(red: DoubleArray, ir: DoubleArray): Boolean {
        val redDc = red.average()
        val irDc = ir.average()
        if (redDc <= 0 || irDc <= 0) return false

        val redRel = std(red) / redDc
        val irRel = std(ir) / irDc

        // Too flat (< 0.3%) or Too Noisy (> 20%)
        if (redRel < 0.003 || irRel < 0.003) return false
        if (redRel > 0.2 || irRel > 0.2) return false

        return true
    }

    private fun rorToSpo2(R: Double, R0: Double): Double {
        if (R.isNaN() || R0.isNaN()) return Double.NaN
        val targetSpo2 = 97.0
        val slope = 12.0
        val raw = targetSpo2 - slope * (R - R0)
        return softClip(raw)
    }

    private fun softClip(x: Double, low: Double = 90.0, high: Double = 100.0, k: Double = 1.5): Double {
        val mid = (low + high) / 2.0
        return low + (high - low) / (1.0 + exp(-k * (x - mid)))
    }

    private fun std(arr: DoubleArray): Double {
        val avg = arr.average()
        var sum = 0.0
        for (v in arr) sum += (v - avg) * (v - avg)
        return sqrt(sum / arr.size)
    }
}