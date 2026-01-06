package com.samsung.health.mobile

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

object SimulatedDataProvider {
    fun getHealthStream(): Flow<HealthSnapshot> = flow {
        var counter = 0.0

        // Tab 1 Buffers
        val ecgBuffer = MutableList(100) { 0f }
        val hrBuffer = MutableList(60) { 70f }

        // Tab 2 Buffers (History for graphs)
        val sqiBuffer = MutableList(60) { 0.8f }
        val psiBuffer = MutableList(60) { 0.3f }
        val cvhsBuffer = MutableList(60) { 0.9f }
        val clsBuffer = MutableList(60) { 0.4f }
        val evsBuffer = MutableList(60) { 0.7f }

        while (true) {
            counter += 0.1

            // --- Tab 1 Simulation ---
            val currentHr = (80 + 10 * sin(counter * 0.5) + Random.nextInt(-2, 3)).toInt()
            hrBuffer.removeAt(0); hrBuffer.add(currentHr.toFloat())

            // Fixed: Explicit Double math for sin()
            val ecgPoint = (sin(counter * 5) + 0.5 * sin(counter * 10) +
                    if (counter.rem(3.14) < 0.2) 2.0 else 0.0).toFloat()
            ecgBuffer.removeAt(0); ecgBuffer.add(ecgPoint)

            // --- Tab 2 Simulation (Fixed Type Mismatch) ---
            // We calculate in Double, then cast to Float at the end
            val sqi = (0.7 + 0.1 * sin(counter * 0.1)).coerceIn(0.0, 1.0).toFloat()
            val psi = (0.4 + 0.1 * sin(counter * 0.15 + 2)).coerceIn(0.0, 1.0).toFloat()
            val cvhs = (0.85 + 0.1 * sin(counter * 0.05)).coerceIn(0.0, 1.0).toFloat()
            val cls = (0.5 + 0.1 * sin(counter * 0.2)).coerceIn(0.0, 1.0).toFloat()
            val evs = (0.6 + 0.1 * sin(counter * 0.08 + 4)).coerceIn(0.0, 1.0).toFloat()

            // Update buffers
            updateBuffer(sqiBuffer, sqi)
            updateBuffer(psiBuffer, psi)
            updateBuffer(cvhsBuffer, cvhs)
            updateBuffer(clsBuffer, cls)
            updateBuffer(evsBuffer, evs)

            emit(
                HealthSnapshot(
                    timestamp = System.currentTimeMillis(),
                    heartRate = currentHr,
                    heartRateHistory = hrBuffer.toList(),
                    spo2 = 96 + Random.nextInt(0, 4),
                    ecgSignal = ecgBuffer.toList(),
                    accMagnitude = abs(sin(counter).toFloat()),
                    skinTemperature = 36.5f + (Random.nextFloat() * 0.5f),
                    respirationRate = 12 + Random.nextInt(0, 6),
                    // New Indices
                    sqi = sqi, sqiHistory = sqiBuffer.toList(),
                    psi = psi, psiHistory = psiBuffer.toList(),
                    cvhs = cvhs, cvhsHistory = cvhsBuffer.toList(),
                    cls = cls, clsHistory = clsBuffer.toList(),
                    evs = evs, evsHistory = evsBuffer.toList()
                )
            )
            delay(2000)
        }
    }

    private fun updateBuffer(buffer: MutableList<Float>, newValue: Float) {
        buffer.removeAt(0)
        buffer.add(newValue)
    }
}