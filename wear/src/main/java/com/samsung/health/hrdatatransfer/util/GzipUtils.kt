package com.samsung.health.hrdatatransfer.util

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

object GzipUtils {
    fun compress(stringData: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(stringData.toByteArray(StandardCharsets.UTF_8))
        }
        return byteArrayOutputStream.toByteArray()
    }
}