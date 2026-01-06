// --- src/main/java/com/samsung/health/mobile/DataReceiverService.kt ---
package com.samsung.health.mobile

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.samsung.health.mobile.util.PerformanceMonitor // Import Monitor
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
            val packetSize = event.data.size.toLong() // Capture size
            Log.i("Receiver", "Packet received: $packetSize bytes")

            scope.launch {
                try {
                    // 1. Decompress
                    val jsonString = GzipUtils.decompress(event.data)

                    // 2. Deserialize
                    val batch = Json.decodeFromString<RawSensorBatch>(jsonString)

                    // 3. Save
                    storageManager.saveBatch(batch)

                    // 4. REPORT TO MONITOR (Now with Latency!)
                    // We pass the packet size AND the timestamp the watch sent it (batchId)
                    PerformanceMonitor.logPacket(packetSize, batch.batchId)

                    Log.d("Receiver", "✅ Batch ${batch.batchId} saved.")

                } catch (e: Exception) {
                    if (event.data.isNotEmpty() && event.data[0] == 1.toByte()) {
                        Log.w("Receiver", "⚠️ Ignored legacy BINARY packet.")
                    } else {
                        Log.e("Receiver", "❌ Parsing failed: ${e.message}")
                    }
                }
            }
        }
    }
}