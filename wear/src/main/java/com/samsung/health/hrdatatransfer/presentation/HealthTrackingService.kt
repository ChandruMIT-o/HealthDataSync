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
import android.os.PowerManager
import android.content.Context

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
    @Inject
    lateinit var trackingRepository: TrackingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wakeLock: PowerManager.WakeLock? = null

    private var connectionJob: Job? = null
    private var trackingJob: Job? = null
    private var senderJob: Job? = null

    private var phoneNode: Node? = null
    private val dataBuffer = mutableListOf<HealthDataRecord>()
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

        // --- Acquire WakeLock ---
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HealthTracking::MyWakelockTag" // A unique tag for debugging
            )
            wakeLock?.setReferenceCounted(false) // We'll manage its lifecycle
            wakeLock?.acquire() // This forces the CPU to stay on
            Log.i(TAG, "WakeLock acquired!")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
        // --- End of new code ---

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
            Log.w(TAG, "Phone node is null. Retrying find...")
            findPhoneNode()
            if (phoneNode == null) {
                // PHONE IS DISCONNECTED
                Log.d(TAG, "Buffering data. Buffer size: ${dataBuffer.size}")
                dataBuffer.add(record)
                // We DO NOT flush. This lets the SDK batch sensors and save battery.
                return
            }
        }

        // PHONE IS CONNECTED
        var successfullySent = false

        // --- 1. Try to send the buffer first ---
        if (dataBuffer.isNotEmpty()) {
            Log.i(TAG, "Reconnected. Sending buffer of ${dataBuffer.size} records...")
            val jsonString = Json.encodeToString(dataBuffer)

            // We use the MessageRepository directly to see if it succeeded
            successfullySent = messageRepository.sendMessage(jsonString, phoneNode!!, MESSAGE_PATH)

            if (successfullySent) {
                dataBuffer.clear() // Clear buffer on success
            } else {
                Log.e(TAG, "Failed to send buffer, will retry.")
                dataBuffer.add(record) // Add current record to buffer and give up for now
                return // DO NOT flush
            }
        }

        // --- 2. Send the current record ---
        try {
            val dataList = listOf(record)
            val jsonString = Json.encodeToString(dataList)
            successfullySent = messageRepository.sendMessage(jsonString, phoneNode!!, MESSAGE_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data record", e)
            successfullySent = false
        }

        // --- 3. Flush sensors ONLY on success ---
        if (successfullySent) {
            // IT WORKED! Flush sensors to get the next real-time packet.
            Log.d(TAG, "Send success, flushing sensors.")
            trackingRepository.flushTrackers()
        } else {
            // IT FAILED! Buffer this record and DO NOT flush.
            Log.w(TAG, "Send failed, buffering record.")
            dataBuffer.add(record)
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

        // --- Release WakeLock ---
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WakeLock released.")
            }
        }
        wakeLock = null
        // --- End of new code ---

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