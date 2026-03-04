package com.samsung.health.mobile

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.samsung.health.mobile.util.PerformanceMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class DataReceiverService : WearableListenerService() {

    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var repository: HealthDataRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/raw_data_stream") {
            val packetSize = event.data.size.toLong()
            Log.i("Receiver", "Packet received: $packetSize bytes")

            scope.launch {
                try {
                    val jsonString = GzipUtils.decompress(event.data)
                    val batch = Json.decodeFromString<RawSensorBatch>(jsonString)

                    // 1. SAVE RAW DATA (Backup/Validation)
                    // Saves to "raw_data_current.csv" (Limit 80MB)
                    storageManager.saveRawBatch(batch)

                    PerformanceMonitor.logPacket(packetSize, batch.batchId)

                    // 2. SEND TO PIPELINE
                    // Processes for UI and saves clean Minute Batches (Limit 20MB)
                    repository.processAndQueueBatch(batch)

                    Log.d("Receiver", "✅ Batch ${batch.batchId} saved raw & queued for processing.")

                } catch (e: Exception) {
                    Log.e("Receiver", "❌ Processing failed: ${e.message}")
                }
            }
        }
    }
}