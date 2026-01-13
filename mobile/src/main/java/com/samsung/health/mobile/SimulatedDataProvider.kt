package com.samsung.health.mobile

import kotlin.math.sin
import kotlin.random.Random

/**
 * ONLY provides simulated ECG data.
 * All other sensor simulation has been removed.
 */
object SimulatedDataProvider {
    private var counter = 0.0

    // Generate a chunk of ECG data to keep the graph alive
    fun getEcgBatch(size: Int = 10): List<Float> {
        val ecgData = ArrayList<Float>()
        for (i in 0 until size) {
            counter += 0.1
            // Simple PQRST-ish simulation
            val ecgPoint = (sin(counter * 5) + 0.5 * sin(counter * 10) +
                    if (counter.rem(3.14) < 0.2) 2.0 else 0.0).toFloat()
            ecgData.add(ecgPoint)
        }
        return ecgData
    }
}