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

object GzipUtils {
    fun decompress(compressed: ByteArray): String {
        return try {
            // 1. Try to decompress
            val bis = ByteArrayInputStream(compressed)
            val gis = GZIPInputStream(bis)
            val br = gis.bufferedReader(StandardCharsets.UTF_8)
            br.use { it.readText() }
        } catch (e: Exception) {
            // 2. If it fails (Not in GZIP format), assume it is just a plain String
            String(compressed, StandardCharsets.UTF_8)
        }
    }
}