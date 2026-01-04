package com.samsung.health.hrdatatransfer.data.model

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawSensorBatch
        if (batchId != other.batchId) return false
        if (!ppgGreen.contentEquals(other.ppgGreen)) return false
        if (!accX.contentEquals(other.accX)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = batchId.hashCode()
        result = 31 * result + ppgGreen.contentHashCode()
        result = 31 * result + accX.contentHashCode()
        return result
    }
}