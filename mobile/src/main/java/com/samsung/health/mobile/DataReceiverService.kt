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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/raw_data_stream") {
            Log.i("Receiver", "Data received: ${event.data.size} bytes")
            scope.launch {
                try {
                    val jsonString = GzipUtils.decompress(event.data)
                    val batch = Json.decodeFromString<RawSensorBatch>(jsonString)
                    storageManager.saveBatch(batch)
                } catch (e: Exception) {
                    Log.e("Receiver", "Parsing failed", e)
                }
            }
        }
    }
}