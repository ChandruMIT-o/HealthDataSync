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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file = File(context.filesDir, "raw_data.csv")

    private val _fileSize = MutableStateFlow(0L)
    val fileSize = _fileSize.asStateFlow()

    private val _lastUpdate = MutableStateFlow(0L)
    val lastUpdate = _lastUpdate.asStateFlow()

    init {
        updateFileSize()
        if (!file.exists()) {
            // UPDATED HEADER: Added Red, IR, AccY, AccZ columns
            file.writeText("BatchID_Epoch,Time,DurationMs,PPG_Count,ACC_Count,Temp_Count,PPG_Green_Arr,PPG_Red_Arr,PPG_IR_Arr,ACC_X_Arr,ACC_Y_Arr,ACC_Z_Arr,Temp_Arr\n")
        }
    }

    suspend fun saveBatch(batch: RawSensorBatch) = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(batch.batchId))
            val sb = StringBuilder()

            // 1. Metadata
            sb.append("${batch.batchId},")
            sb.append("$dateStr,")
            sb.append("${batch.durationMs},")

            // 2. Data Counts (Length of arrays)
            sb.append("${batch.ppgGreen.size},")
            sb.append("${batch.accX.size},")
            sb.append("${batch.skinTemp.size},")

            // 3. PPG Data (Green, Red, IR)
            sb.append("\"[${batch.ppgGreen.joinToString(";")}]\",")
            sb.append("\"[${batch.ppgRed.joinToString(";")}]\",")   // <--- ADDED
            sb.append("\"[${batch.ppgIr.joinToString(";")}]\",")    // <--- ADDED

            // 4. Accelerometer Data (X, Y, Z)
            sb.append("\"[${batch.accX.joinToString(";")}]\",")
            sb.append("\"[${batch.accY.joinToString(";")}]\",")     // <--- ADDED
            sb.append("\"[${batch.accZ.joinToString(";")}]\",")     // <--- ADDED

            // 5. Temperature Data
            sb.append("\"[${batch.skinTemp.joinToString(";")}]\"\n")

            file.appendText(sb.toString())
            updateFileSize()
            _lastUpdate.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("StorageManager", "Failed to save batch", e)
        }
    }

    suspend fun exportToDownloads(): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext "No data to export"

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "HealthData_$timestamp.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext "Failed to create file"

            resolver.openOutputStream(uri).use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output!!)
                }
            }

            return@withContext "Saved to Downloads/$fileName"
        } catch (e: Exception) {
            return@withContext "Export Error: ${e.message}"
        }
    }

    fun clearData() {
        if (file.exists()) file.delete()
        updateFileSize()
        // Re-create the header immediately after clearing, so the file is ready for new data
        if (!file.exists()) {
            file.writeText("BatchID_Epoch,Time,DurationMs,PPG_Count,ACC_Count,Temp_Count,PPG_Green_Arr,PPG_Red_Arr,PPG_IR_Arr,ACC_X_Arr,ACC_Y_Arr,ACC_Z_Arr,Temp_Arr\n")
        }
    }

    private fun updateFileSize() {
        _fileSize.value = if (file.exists()) file.length() else 0L
    }
}