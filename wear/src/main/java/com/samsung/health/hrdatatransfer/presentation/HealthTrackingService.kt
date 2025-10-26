package com.samsung.health.hrdatatransfer.presentation

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Node
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.NOTIFICATION_CHANNEL_ID
import com.samsung.health.hrdatatransfer.R
import com.samsung.health.hrdatatransfer.data.*
import com.samsung.health.hrdatatransfer.domain.StartTrackingUseCase
import com.samsung.health.hrdatatransfer.domain.StopTrackingUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "HealthTrackingService"
private const val NOTIFICATION_ID = 1
private const val MESSAGE_PATH = "/msg"
private const val CAPABILITY_WEAR_APP = "wear"

@AndroidEntryPoint
class HealthTrackingService : Service() {

    // All dependencies are injected here, NOT in the ViewModel
    @Inject
    lateinit var healthTrackingServiceConnection: HealthTrackingServiceConnection
    @Inject
    lateinit var startTrackingUseCase: StartTrackingUseCase
    @Inject
    lateinit var stopTrackingUseCase: StopTrackingUseCase
    @Inject
    lateinit var messageRepository: MessageRepository
    @Inject
    lateinit var capabilityRepository: CapabilityRepository
    @Inject
    lateinit var trackingStateHolder: TrackingStateHolder // The singleton state holder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var connectionJob: Job? = null
    private var trackingJob: Job? = null
    private var senderJob: Job? = null

    private var phoneNode: Node? = null
    private val selectedSensors = setOf(
        HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        HealthTrackerType.HEART_RATE_CONTINUOUS,
        HealthTrackerType.PPG_CONTINUOUS,
        HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
        HealthTrackerType.EDA_CONTINUOUS
    )

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        // ▼▼▼ FIX: ADD THIS NEW ACTION ▼▼▼
        const val ACTION_PREPARE = "ACTION_PREPARE"
        // ▲▲▲ END FIX ▲▲▲
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        startForeground(NOTIFICATION_ID, createNotification())
        connectToHealthService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            // ▼▼▼ FIX: ADD THIS 'WHEN' BRANCH ▼▼▼
            ACTION_PREPARE -> {
                // Do nothing.
                // onCreate() already called startForeground() and connectToHealthService().
                // This action just ensures the service is started and running.
                Log.i(TAG, "Service is preparing...")
            }
            // ▲▲▲ END FIX ▲▲▲
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTrackingAndService()
        }
        return START_NOT_STICKY // Don't restart if killed
    }

    private fun connectToHealthService() {
        if (connectionJob?.isActive == true) return

        connectionJob = serviceScope.launch {
            findPhoneNode() // Find the phone node once on connection
            trackingStateHolder.setTrackingState(TrackingState.Connecting)
            healthTrackingServiceConnection.connectionFlow.collect { message ->
                when (message) {
                    is ConnectionMessage.ConnectionSuccessMessage -> {
                        Log.i(TAG, "Connection Success.")
                        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = false))
                    }
                    is ConnectionMessage.ConnectionFailedMessage -> {
                        Log.e(TAG, "Connection Failed", message.exception)
                        trackingStateHolder.setTrackingState(TrackingState.Failed(message.exception))
                    }
                    is ConnectionMessage.ConnectionEndedMessage -> {
                        Log.w(TAG, "Connection Ended.")
                        trackingStateHolder.setTrackingState(TrackingState.Disconnected)
                    }
                }
            }
        }
    }

    private fun startTracking() {
        Log.i(TAG, "Starting tracking...")
        trackingJob?.cancel()

        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true))

        trackingJob = startTrackingUseCase(selectedSensors)
            .onEach { record ->
                // Update state holder with new data
                trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true, latestData = record))

                // Launch the send job (using the 'collectLatest'-style pattern)
                senderJob?.cancel()
                senderJob = serviceScope.launch {
                    sendDataRecord(record)
                }
            }
            .catch { e -> Log.e(TAG, "Tracking Flow error", e) }
            .launchIn(serviceScope)
    }

    private suspend fun sendDataRecord(record: HealthDataRecord) {
        if (phoneNode == null) {
            Log.w(TAG, "Cannot send data: phoneNode is null. Retrying find...")
            findPhoneNode() // Try to find it again
            if (phoneNode == null) return // Give up if still not found
        }
        try {
            val dataList = listOf(record)
            val jsonString = Json.encodeToString(dataList)
            messageRepository.sendMessage(jsonString, phoneNode!!, MESSAGE_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data record", e)
        }
    }

    private suspend fun findPhoneNode() {
        try {
            val nodes = capabilityRepository.getCapabilitiesForReachableNodes()
                .filterValues { CAPABILITY_WEAR_APP in it }
                .keys
            phoneNode = if (nodes.isEmpty()) {
                Log.w(TAG, "No phone node found with 'wear' capability.")
                null
            } else {
                Log.i(TAG, "Phone node found: ${nodes.first().displayName}")
                nodes.first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding phone node", e)
        }
    }

    private fun stopTrackingAndService() {
        Log.i(TAG, "Stopping tracking and service...")
        trackingJob?.cancel()
        senderJob?.cancel()
        connectionJob?.cancel()

        stopTrackingUseCase()

        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = false))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.w(TAG, "Service onDestroy")
        // Ensure everything is cleaned up
        trackingJob?.cancel()
        senderJob?.cancel()
        connectionJob?.cancel()
        stopTrackingUseCase()
        trackingStateHolder.setTrackingState(TrackingState.Disconnected) // Inform UI
        serviceScope.cancel() // Cancel all coroutines
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Health Stream Active")
            .setContentText("Streaming data to phone...")
            .setSmallIcon(R.mipmap.ic_launcher) // Use your app's icon
            .setOngoing(true)
            .build()
    }

    // A bound service is not needed for this simple "start/stop" model
    override fun onBind(intent: Intent?): IBinder? = null
}