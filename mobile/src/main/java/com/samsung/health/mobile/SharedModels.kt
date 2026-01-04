package com.samsung.health.mobile

import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

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
