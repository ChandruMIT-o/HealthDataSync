package com.samsung.health.mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GzipUtils {

    /**
     * Decompresses a Gzip-compressed ByteArray back into a String (JSON).
     */
    fun decompress(compressed: ByteArray): String {
        return try {
            val bis = ByteArrayInputStream(compressed)
            val gis = GZIPInputStream(bis)
            val br = gis.bufferedReader(StandardCharsets.UTF_8)
            br.use { it.readText() }
        } catch (e: Exception) {
            // Fallback: If decompression fails, return an empty JSON object or rethrow
            // to prevent the JSON parser from crashing on binary data.
            throw IllegalArgumentException("Failed to decompress data. Is it valid GZIP?", e)
        }
    }

    /**
     * (Optional) Compresses a String into Gzip bytes.
     * Useful if you ever need to send data BACK to the watch.
     */
    fun compress(stringData: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(stringData.toByteArray(StandardCharsets.UTF_8))
        }
        return byteArrayOutputStream.toByteArray()
    }
}