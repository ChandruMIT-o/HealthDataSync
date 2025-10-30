package com.samsung.health.mobile.data

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.samsung.health.mobile.presentation.ProcessingStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DataListenerService"
private const val MESSAGE_PATH = "/msg"

// ▼▼▼ FIX: Add @AndroidEntryPoint ▼▼▼
@AndroidEntryPoint
class DataListenerService : WearableListenerService() {

    // ▼▼▼ FIX: Inject the StateHolder ▼▼▼
    @Inject
    lateinit var stateHolder: ProcessingStateHolder
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // ▲▲▲ END FIX ▲▲▲

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val value = messageEvent.data.decodeToString()
        Log.i(TAG, "onMessageReceived(): ${messageEvent.path}")

        when (messageEvent.path) {
            MESSAGE_PATH -> {
                Log.i(TAG, "Service: message (/msg) received. Posting to inbox.")
                if (value.isNotEmpty()) {
                    // ▼▼▼ FIX: Post to the inbox instead of starting a service ▼▼▼
                    serviceScope.launch {
                        try {
                            stateHolder.postData(value)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to post data to inbox", e)
                        }
                    }
                    // ▲▲▲ END FIX ▲▲▲
                } else {
                    Log.w(TAG, "Received message with empty value.")
                }
            }
        }
    }

    // ▼▼▼ FIX: Clean up the coroutine scope ▼▼▼
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    // ▲▲▲ END FIX ▲▲▲
}