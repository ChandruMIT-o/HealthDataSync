// --- src\main\java\com\samsung\health\mobile\presentation\ProcessingService.kt ---
package com.samsung.health.mobile.presentation

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DatabaseReference
import com.samsung.health.data.TrackedData // Make sure this class has your new fields
import com.samsung.health.mobile.NOTIFICATION_CHANNEL_ID
import com.samsung.health.mobile.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "ProcessingService"
private const val NOTIFICATION_ID = 1

@AndroidEntryPoint
class ProcessingService : Service() {
    @Inject
    lateinit var databaseReference: DatabaseReference
    @Inject
    lateinit var stateHolder: ProcessingStateHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var inboxCollectorJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        // Ticker is no longer needed
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: ${intent?.action}")
        startForeground(NOTIFICATION_ID, createNotification())
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Service started or prepared by UI.")
                startInboxCollector()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInboxCollector() {
        if (inboxCollectorJob?.isActive == true) return
        inboxCollectorJob = serviceScope.launch {
            Log.i(TAG, "Starting inbox collector...")
            stateHolder.dataInbox.collect { jsonString ->
                Log.d(TAG, "Inbox collector received new data.")
                processIncomingData(jsonString)
            }
        }
    }

    /**
     * This function now does ALL the work.
     * It no longer buffers data. It processes and saves immediately.
     */
    private fun processIncomingData(jsonString: String) {
        try {
            val allData = HelpFunctions.decodeMessage(jsonString)
            // The watch sends a list, but it's a list of 1 processed record
            val record = allData.firstOrNull()

            if (record == null) {
                Log.w(TAG, "Received empty data list.")
                return
            }

            // Filter out "off-wrist" or invalid data
            if (record.hr == null || record.hr == 0) {
                Log.d(TAG, "Received record, but filtered out (HR=0 or null).")
                // You might want to update UI with a "---" record
                stateHolder.setLatestAveragedData(record)
                return
            }

            Log.d(TAG, "Processing record with HR: ${record.hr} and SpO2: ${record.spo2}")

            // 1. Update the UI
            stateHolder.setLatestAveragedData(record)

            // 2. Save to database (this is now done here)
            saveToRealtimeDatabase(record)

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding or processing data", e)
        }
    }

    // This function is now called directly from processIncomingData
    private fun saveToRealtimeDatabase(data: TrackedData) {
        serviceScope.launch {
            stateHolder.setSavingState(true)
            try {
                databaseReference.push().setValue(data).await()
                Log.d("RTDB", "Successfully saved data: ${data.timestamp}")
            } catch (e: Exception) {
                Log.e("RTDB", "Error saving data", e)
            } finally {
                stateHolder.setSavingState(false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inboxCollectorJob?.cancel()
        serviceScope.cancel()
        Log.w(TAG, "Service destroyed.")
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Health Data Receiver")
            .setContentText("Receiving live data from watch...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}