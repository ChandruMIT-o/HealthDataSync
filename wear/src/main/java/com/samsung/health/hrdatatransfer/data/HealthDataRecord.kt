package com.samsung.health.hrdatatransfer.data
import kotlinx.serialization.Serializable

@Serializable
data class HealthDataRecord(
    val timestamp: Long = 0L,
    var accX: Int? = null,
    var accY: Int? = null,
    var accZ: Int? = null,
    var ppgGreen: Int? = null,
    var ppgIr: Int? = null,
    var ppgRed: Int? = null,
    var hr: Int? = null,
    var ibi: ArrayList<Int>? = null,
    var skinTemp: Float? = null,
    var eda: Float? = null,
    // --- New Fields Added ---
    var ecg: Float? = null,
    var spo2: Float? = null,
    var bvp: Float? = null
)