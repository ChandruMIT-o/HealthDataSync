import kotlinx.serialization.encodeToString
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
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.samsung.health.hrdatatransfer.data.BatchSerializer

private const val TAG = "HealthTrackingService"
private const val NOTIFICATION_ID = 1
private const val MESSAGE_PATH = "/raw_data_stream" // Unique path for raw data
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

    // Coroutine Jobs
    private var connectionJob: Job? = null
    private var trackingJob: Job? = null
    private var senderJob: Job? = null

    // Transmission State
    private var phoneNode: Node? = null
    // Buffer for batches that failed to send (e.g. phone disconnected)
    private val batchBuffer = ArrayDeque<RawSensorBatch>()

    private val selectedSensors = setOf(
        HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        HealthTrackerType.PPG_CONTINUOUS, // Automatically includes Red/IR/Green via Repository logic
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

        // 1. Acquire WakeLock (CRITICAL for 25Hz background recording)
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

        // 2. Start Foreground immediately
        startForeground(NOTIFICATION_ID, createNotification())

        // 3. Connect to Samsung Health
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
            // Find phone node early so we are ready to stream
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

        // Update UI State
        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true))

        trackingJob = serviceScope.launch(Dispatchers.IO) {
            trackingRepository.track(selectedSensors)
                .catch { e ->
                    Log.e(TAG, "Tracking Flow Exception", e)
                    // If permissions fail or sensor fails, stop everything
                    withContext(Dispatchers.Main) { stopTrackingAndService() }
                }
                .collect { rawBatch ->
                    // This block runs every ~60 seconds when a batch is emitted
                    Log.d(TAG, "Received Raw Batch [ID: ${rawBatch.batchId}]. Sending...")

                    // Update UI (Optional: Just show 'Latest Data Received' timestamp)
                    // We don't pass full RawBatch to UI to save memory, just connection status
                    trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = true, latestData = rawBatch))

                    sendBatch(rawBatch)
                }
        }
    }

    /**
     * Handles the serialization, compression, and transmission of a batch.
     */
    private suspend fun sendBatch(batch: RawSensorBatch) {
        // 1. Retry finding phone if lost
        if (phoneNode == null) findPhoneNode()

        // 2. If still no phone, buffer and return
        if (phoneNode == null) {
            Log.w(TAG, "No phone connected. Buffering batch ${batch.batchId}")
            synchronized(batchBuffer) {
                batchBuffer.add(batch)
                // Optional: Cap buffer size to prevent OOM (e.g., keep last 60 mins)
                if (batchBuffer.size > 60) batchBuffer.removeFirst()
            }
            return
        }

        // 3. Try to send any buffered batches first
        flushBuffer()

        // 4. Send the current batch
        val success = transmitPayload(batch)
        if (!success) {
            Log.w(TAG, "Failed to send batch ${batch.batchId}. Adding to buffer.")
            synchronized(batchBuffer) { batchBuffer.add(batch) }
        }
    }

    // Change "private fun" to "private suspend fun"
    private suspend fun flushBuffer() {
        synchronized(batchBuffer) {
            if (batchBuffer.isEmpty()) return
            Log.i(TAG, "Flushing ${batchBuffer.size} buffered batches...")
        }

        // We must iterate carefully because transmitPayload is suspending.
        // We cannot hold the 'synchronized' lock while calling a suspend function.
        // Strategy: Peek at the first item, try to send it. If success, remove it.

        while (true) {
            val batch = synchronized(batchBuffer) {
                batchBuffer.firstOrNull()
            } ?: break // Buffer empty, we are done

            // This call suspends, allowing other coroutines to run
            val success = transmitPayload(batch)

            if (success) {
                synchronized(batchBuffer) {
                    // Remove the item we just sent
                    if (batchBuffer.isNotEmpty() && batchBuffer.first() == batch) {
                        batchBuffer.removeFirst()
                    }
                }
            } else {
                // If transmission failed, stop flushing. Keep the item in the buffer.
                break
            }
        }
    }

    private suspend fun transmitPayload(batch: RawSensorBatch): Boolean {
        try {
            // 1. Serialize to JSON
            val jsonString = Json.encodeToString(batch)

            // 2. Compress (The step we removed later)
            val compressedBytes = GzipUtils.compress(jsonString)

            Log.d(TAG, "Transmitting Batch ${batch.batchId} | " +
                    "Raw: ${jsonString.length} chars | " +
                    "Compressed: ${compressedBytes.size} bytes")

            // 3. Size Check (Wear OS Limit is ~100KB)
            if (compressedBytes.size > 95 * 1024) {
                Log.e(TAG, "❌ Batch ${batch.batchId} is TOO LARGE (${compressedBytes.size} bytes). Dropping.")
                return true // Return true to discard this oversized batch so we don't loop forever
            }

            // 4. Send Compressed Bytes
            val success = messageRepository.sendMessageBytes(
                compressedBytes,
                phoneNode!!,
                MESSAGE_PATH
            )

            if (success) {
                Log.d(TAG, "✅ Sent Batch ${batch.batchId}")
            } else {
                Log.w(TAG, "⚠️ Transmission returned false (Remote node might be unreachable)")
            }

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ Transmission Error for Batch ${batch.batchId}", e)
            return false // This triggers the service to add this batch to the buffer
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

        // 1. Cancel Coroutines
        trackingJob?.cancel()
        senderJob?.cancel()
        connectionJob?.cancel()

        // 2. Stop Sensor Tracking
        trackingRepository.stopTracking()

        // 3. Reset State
        trackingStateHolder.setTrackingState(TrackingState.Connected(isTracking = false))

        // 4. Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }

        // 5. Stop Foreground
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