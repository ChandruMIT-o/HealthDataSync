// --- src/main/java/com/samsung/health/mobile/SharedModels.kt ---
package com.samsung.health.mobile

import kotlinx.serialization.Serializable

@Serializable
data class RawSensorBatch(
    val batchId: Long,
    val durationMs: Int = 60_000,
    val ppgTimestamp: Long,
    val ppgGreen: FloatArray,
    val ppgRed: FloatArray,
    val ppgIr: FloatArray,
    val accTimestamp: Long,
    val accX: FloatArray,
    val accY: FloatArray,
    val accZ: FloatArray,
    val tempTimestamp: Long,
    val skinTemp: FloatArray
)

data class HealthSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    // Tab 1 Vitals
    val heartRate: Int = 0,
    val heartRateHistory: List<Float> = emptyList(),
    val spo2: Int = 0,
    val ecgSignal: List<Float> = emptyList(),
    val accMagnitude: Float = 0f,
    val skinTemperature: Float = 0f,
    val respirationRate: Int = 0,
    val edaValue: Float = 0f,
    val ibi: Float = 0f,

    // Tab 2 Indices (0.0 - 1.0 Range)
    val sqi: Float = 0f, // Sleep Quality
    val sqiHistory: List<Float> = emptyList(),

    val psi: Float = 0f, // Psychosomatic Stress
    val psiHistory: List<Float> = emptyList(),

    val cvhs: Float = 0f, // Cardiovascular Health
    val cvhsHistory: List<Float> = emptyList(),

    val cls: Float = 0f, // Cognitive Load
    val clsHistory: List<Float> = emptyList(),

    val evs: Float = 0f, // Emotional Vitality
    val evsHistory: List<Float> = emptyList()
)

// In SharedModels.kt
data class MinuteBatch(
    val startTimestamp: Long,      // The epoch ms when this minute started

    // The Time Axis (X-Axis) - This was missing!
    val timestamps: List<Long>,

    // The Data Axes (Y-Axes) - Full Resolution Arrays
    val hrValues: List<Double>,
    val spo2Values: List<Double>,
    val rrValues: List<Double>,
    val movementValues: List<Double>,
    val tempValues: List<Double>,

    // Flattened IBI stream
    val ibiStream: List<Double>
)