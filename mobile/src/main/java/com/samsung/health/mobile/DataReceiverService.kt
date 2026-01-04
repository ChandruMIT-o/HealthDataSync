package com.samsung.health.mobile

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
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

    // SupervisorJob ensures a crash in one save doesn't kill the whole scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/raw_data_stream") {
            val size = event.data.size
            Log.i("Receiver", "Packet received: $size bytes")

            scope.launch {
                try {
                    // 1. Decompress
                    // If this throws, it means the data isn't valid GZIP (likely old binary data)
                    val jsonString = GzipUtils.decompress(event.data)

                    // 2. Deserialize
                    val batch = Json.decodeFromString<RawSensorBatch>(jsonString)

                    // 3. Save
                    storageManager.saveBatch(batch)
                    Log.d("Receiver", "✅ Batch ${batch.batchId} saved.")

                } catch (e: Exception) {
                    // Smart Error Handling:
                    // Check if the data starts with '1' (The old Binary Serializer version)
                    if (event.data.isNotEmpty() && event.data[0] == 1.toByte()) {
                        Log.w("Receiver", "⚠️ Ignored legacy BINARY packet. Waiting for new GZIP data...")
                    } else {
                        Log.e("Receiver", "❌ Parsing failed: ${e.message}")
                    }
                }
            }
        }
    }
}