package com.samsung.health.mobile.pipeline

import kotlin.math.*

object SpO2Estimator {

    fun estimate(red: FloatArray, ir: FloatArray): Double? {
        val redD = red.map { it.toDouble() }.toDoubleArray()
        val irD = ir.map { it.toDouble() }.toDoubleArray()

        val redDC = redD.average()
        val irDC = irD.average()

        val redAC = rms(
            SignalProcessing.butterBandpass(redD, 0.7, 3.0)
        )
        val irAC = rms(
            SignalProcessing.butterBandpass(irD, 0.7, 3.0)
        )

        val r = (redAC / redDC) / (irAC / irDC)
        val spo2 = 110.0 - 25.0 * r

        return if (spo2 in 70.0..100.0) spo2 else null
    }

    private fun rms(x: DoubleArray): Double =
        sqrt(x.map { it * it }.average())
}

