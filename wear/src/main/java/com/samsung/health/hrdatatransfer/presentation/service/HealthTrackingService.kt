package com.samsung.health.hrdatatransfer.presentation.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Node
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.NOTIFICATION_CHANNEL_ID
import com.samsung.health.hrdatatransfer.R
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch
import com.samsung.health.hrdatatransfer.domain.repository.MessageRepository
import com.samsung.health.hrdatatransfer.data.repository.CapabilityRepository
import com.samsung.health.hrdatatransfer.data.service.ConnectionMessage
import com.samsung.health.hrdatatransfer.data.service.HealthTrackingServiceConnection
import com.samsung.health.hrdatatransfer.domain.repository.TrackingRepository
import com.samsung.health.hrdatatransfer.presentation.TrackingState
import com.samsung.health.hrdatatransfer.presentation.TrackingStateHolder
import com.samsung.health.hrdatatransfer.util.GzipUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "HealthTrackingService"
private const val NOTIFICATION_ID = 1
private const val MESSAGE_PATH = "/raw_data_stream"
private const val CAPABILITY_WEAR_APP = "wear"

@AndroidEntryPoint
class HealthTrackingService : Service() {

    @Inject lateinit var healthTrackingServiceConnection: HealthTrackingServiceConnection
    @Inject lateinit var trackingRepository: TrackingRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var capabilityRepository: CapabilityRepository
    @Inject lateinit var trackingStateHolder: TrackingStateHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionJob: Job? = null
    private var trackingJob: Job? = null
    private var senderJob: Job? = null
    private var phoneNode: Node? = null

    private val batchBuffer = ArrayDeque<RawSensorBatch>()

    private val selectedSensors = setOf(
        HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        HealthTrackerType.PPG_CONTINUOUS,
        HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS
    )

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PREPARE = "ACTION_PREPARE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service Created. Initializing WakeLock and Connection.")
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HealthTracking::RawDataWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i(TAG, "WakeLock acquired.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }

        startForeground(NOTIFICATION_ID, createNotification())
        connectToHealthService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> Log.i(TAG, "Service Preparing...")
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTrackingAndService()
        }
        return START_NOT_STICKY
    }

    private fun connectToHealthService() {
        if (connectionJob?.isActive == true) return
        connectionJob = serviceScope.launch {
            findPhoneNode()
            trackingStateHolder.setTrackingState(TrackingState.Connecting)
            healthTrackingServiceConnection.connectionFlow.collect { message ->
                when (message) {
                    is ConnectionMessage.ConnectionSuccessMessage -> {
                        Log.i(TAG, "Connected to Samsung Health SDK.")
                        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = false))
                    }
                    is ConnectionMessage.ConnectionFailedMessage -> {
                        Log.e(TAG, "Samsung Health SDK Connection Failed", message.exception)
                        trackingStateHolder.setTrackingState(TrackingState.Failed(message.exception))
                    }
                    is ConnectionMessage.ConnectionEndedMessage -> {
                        Log.w(TAG, "Samsung Health SDK Connection Ended.")
                        trackingStateHolder.setTrackingState(TrackingState.Disconnected)
                    }
                }
            }
        }
    }

    private fun startTracking() {
        Log.i(TAG, "Starting Raw Data Tracking...")
        if (trackingJob?.isActive == true) {
            Log.w(TAG, "Tracking already in progress.")
            return
        }

        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true))

        trackingJob = serviceScope.launch(Dispatchers.IO) {
            trackingRepository.track(selectedSensors)
                .catch { e ->
                    Log.e(TAG, "Tracking Flow Exception", e)
                    withContext(Dispatchers.Main) { stopTrackingAndService() }
                }
                .collect { rawBatch ->
                    Log.d(TAG, "Received Raw Batch [ID: ${rawBatch.batchId}]. Sending...")
                    trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true, latestData = rawBatch))
                    sendBatch(rawBatch)
                }
        }
    }

    private suspend fun sendBatch(batch: RawSensorBatch) {
        if (phoneNode == null) findPhoneNode()

        if (phoneNode == null) {
            Log.w(TAG, "No phone connected. Buffering batch ${batch.batchId}")
            synchronized(batchBuffer) {
                batchBuffer.add(batch)
                if (batchBuffer.size > 60) batchBuffer.removeFirst()
            }
            return
        }

        flushBuffer()
        val success = transmitPayload(batch)
        if (!success) {
            Log.w(TAG, "Failed to send batch ${batch.batchId}. Adding to buffer.")
            synchronized(batchBuffer) { batchBuffer.add(batch) }
        }
    }

    private suspend fun flushBuffer() {
        synchronized(batchBuffer) {
            if (batchBuffer.isEmpty()) return
            Log.i(TAG, "Flushing ${batchBuffer.size} buffered batches...")
        }

        while (true) {
            val batch = synchronized(batchBuffer) {
                batchBuffer.firstOrNull()
            } ?: break

            val success = transmitPayload(batch)
            if (success) {
                synchronized(batchBuffer) {
                    if (batchBuffer.isNotEmpty() && batchBuffer.first() == batch) {
                        batchBuffer.removeFirst()
                    }
                }
            } else {
                break
            }
        }
    }

    private suspend fun transmitPayload(batch: RawSensorBatch): Boolean {
        try {
            // 1. Serialize to JSON
            val jsonString = Json.encodeToString(batch)

            // 2. Compress (Gzip)
            val compressedBytes = GzipUtils.compress(jsonString)

            Log.d(TAG, "Transmitting Batch ${batch.batchId} | " +
                    "Raw JSON: ${jsonString.length} chars | " +
                    "Compressed: ${compressedBytes.size} bytes")

            // 3. Send Compressed Bytes
            val success = messageRepository.sendMessageBytes(
                compressedBytes,
                phoneNode!!,
                MESSAGE_PATH
            )

            if (success) {
                Log.d(TAG, "✅ Sent Batch ${batch.batchId}")
            } else {
                Log.w(TAG, "⚠️ Transmission returned false")
            }
            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Transmission Error for Batch ${batch.batchId}", e)
            return false
        }
    }

    private suspend fun findPhoneNode() {
        try {
            val nodes = capabilityRepository.getCapabilitiesForReachableNodes()
                .filterValues { CAPABILITY_WEAR_APP in it }
                .keys
            phoneNode = nodes.firstOrNull()
            if (phoneNode != null) {
                Log.i(TAG, "Phone Node Found: ${phoneNode?.displayName} [${phoneNode?.id}]")
            } else {
                Log.w(TAG, "No phone node reachable.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding phone node", e)
        }
    }

    private fun stopTrackingAndService() {
        Log.i(TAG, "Stopping Service...")
        trackingJob?.cancel()
        senderJob?.cancel()
        connectionJob?.cancel()
        trackingRepository.stopTracking()
        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = false))

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "Service Destroying.")
        stopTrackingAndService()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MHMS Active")
            .setContentText("Recording High-Res Sensor Data...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}