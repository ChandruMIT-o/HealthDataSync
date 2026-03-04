package com.samsung.health.mobile

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // --- FILE DEFINITIONS ---
    // 1. Raw Data (High Volume) - Limit 80MB (40MB x 2)
    private val rawCurrent = File(context.filesDir, "raw_data_current.csv")
    private val rawBackup = File(context.filesDir, "raw_data_backup.csv")
    private val MAX_RAW_SIZE = 40 * 1024 * 1024L

    // 2. Processed Data (Low Volume) - Limit 20MB (10MB x 2)
    private val procCurrent = File(context.filesDir, "processed_data_current.csv")
    private val procBackup = File(context.filesDir, "processed_data_backup.csv")
    private val MAX_PROC_SIZE = 10 * 1024 * 1024L

    private val _fileSize = MutableStateFlow(0L)
    val fileSize = _fileSize.asStateFlow()

    private val _lastUpdate = MutableStateFlow(0L)
    val lastUpdate = _lastUpdate.asStateFlow()

    // Headers
    private val RAW_HEADER = "BatchID,Time,DurationMs,Green_Arr,Red_Arr,IR_Arr,AccX,AccY,AccZ,Temp_Arr\n"
    private val PROC_HEADER = "Timestamp,Time_UTC,HR_Arr,SpO2_Arr,RR_Arr,Mov_Arr,Temp_Arr,IBI_Stream\n"

    init {
        ensureHeader(rawCurrent, RAW_HEADER)
        ensureHeader(procCurrent, PROC_HEADER)
        updateTotalSize()
    }

    // --- SAVING RAW DATA ---
    suspend fun saveRawBatch(batch: RawSensorBatch) = withContext(Dispatchers.IO) {
        try {
            rotateFile(rawCurrent, rawBackup, MAX_RAW_SIZE, RAW_HEADER)
            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(batch.batchId))

            val sb = StringBuilder()
            sb.append("${batch.batchId},")
            sb.append("$dateStr,")
            sb.append("${batch.durationMs},")
            sb.append("\"${batch.ppgGreen.joinToString(";")}\",")
            sb.append("\"${batch.ppgRed.joinToString(";")}\",")
            sb.append("\"${batch.ppgIr.joinToString(";")}\",")
            sb.append("\"${batch.accX.joinToString(";")}\",")
            sb.append("\"${batch.accY.joinToString(";")}\",")
            sb.append("\"${batch.accZ.joinToString(";")}\",")
            sb.append("\"${batch.skinTemp.joinToString(";")}\"\n")

            rawCurrent.appendText(sb.toString())
            updateTotalSize()
            _lastUpdate.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("StorageManager", "Failed to save RAW batch", e)
        }
    }

    // --- SAVING PROCESSED DATA ---
    suspend fun saveProcessedBatch(batch: MinuteBatch) = withContext(Dispatchers.IO) {
        try {
            rotateFile(procCurrent, procBackup, MAX_PROC_SIZE, PROC_HEADER)
            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(batch.startTimestamp))

            val sb = StringBuilder()
            sb.append("${batch.startTimestamp},")
            sb.append("$dateStr,")
            sb.append("\"${batch.hrValues}\",")
            sb.append("\"${batch.spo2Values}\",")
            sb.append("\"${batch.rrValues}\",")
            sb.append("\"${batch.movementValues}\",")
            sb.append("\"${batch.tempValues}\",")
            sb.append("\"${batch.ibiStream}\"\n")

            procCurrent.appendText(sb.toString())
            updateTotalSize()
        } catch (e: Exception) {
            Log.e("StorageManager", "Failed to save PROC batch", e)
        }
    }

    // --- EXPORT (ZIPS EVERYTHING) ---
    suspend fun exportToDownloads(): String = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "HealthData_$timestamp.zip" // Export as ZIP now

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext "Failed to create file"

            context.contentResolver.openOutputStream(uri).use { os ->
                ZipOutputStream(os).use { zos ->
                    // Helper to add file to zip
                    fun addFile(file: File, name: String) {
                        if (file.exists() && file.length() > 0) {
                            zos.putNextEntry(ZipEntry(name))
                            FileInputStream(file).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                    addFile(rawBackup, "raw_data_part1.csv")
                    addFile(rawCurrent, "raw_data_part2.csv")
                    addFile(procBackup, "processed_metrics_part1.csv")
                    addFile(procCurrent, "processed_metrics_part2.csv")
                }
            }
            return@withContext "Saved ZIP to Downloads"
        } catch (e: Exception) {
            return@withContext "Export Failed: ${e.message}"
        }
    }

    fun clearData() {
        rawCurrent.delete(); rawBackup.delete()
        procCurrent.delete(); procBackup.delete()
        ensureHeader(rawCurrent, RAW_HEADER)
        ensureHeader(procCurrent, PROC_HEADER)
        updateTotalSize()
    }

    // --- UTILS ---
    private fun rotateFile(current: File, backup: File, limit: Long, header: String) {
        if (current.length() > limit) {
            if (backup.exists()) backup.delete()
            current.renameTo(backup)
            ensureHeader(current, header)
        }
    }

    private fun ensureHeader(file: File, header: String) {
        if (!file.exists()) file.writeText(header)
    }

    private fun updateTotalSize() {
        val size = (if(rawCurrent.exists()) rawCurrent.length() else 0) +
                (if(rawBackup.exists()) rawBackup.length() else 0) +
                (if(procCurrent.exists()) procCurrent.length() else 0) +
                (if(procBackup.exists()) procBackup.length() else 0)
        _fileSize.value = size
    }
}