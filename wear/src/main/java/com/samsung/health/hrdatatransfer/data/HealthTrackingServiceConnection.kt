package com.samsung.health.hrdatatransfer.data

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HealthTrackingServiceConnection"

@Singleton
class HealthTrackingServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScope: CoroutineScope // This dependency is back
) {
    private var healthTrackingService: HealthTrackingService? = null

    val connectionFlow = callbackFlow {
        val connectionListener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                Log.i(TAG, "onConnectionSuccess()")
                coroutineScope.runCatching { trySendBlocking(ConnectionMessage.ConnectionSuccessMessage) }
            }

            override fun onConnectionFailed(exception: HealthTrackerException?) {
                Log.e(TAG, "onConnectionFailed()", exception)
                coroutineScope.runCatching { trySendBlocking(ConnectionMessage.ConnectionFailedMessage(exception)) }
            }

            override fun onConnectionEnded() {
                Log.w(TAG, "onConnectionEnded()")
                coroutineScope.runCatching { trySendBlocking(ConnectionMessage.ConnectionEndedMessage) }
                close()
            }
        }
        Log.d(TAG, "Initializing and connecting HealthTrackingService...")
        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService!!.connectService()

        awaitClose {
            Log.d(TAG, "Flow is closing. Disconnecting service.")
            healthTrackingService?.disconnectService()
            healthTrackingService = null
        }
    }

    fun getHealthTrackingService(): HealthTrackingService? {
        return healthTrackingService
    }
}

sealed class ConnectionMessage {
    object ConnectionSuccessMessage : ConnectionMessage()
    data class ConnectionFailedMessage(val exception: HealthTrackerException?) : ConnectionMessage()
    object ConnectionEndedMessage : ConnectionMessage()
}