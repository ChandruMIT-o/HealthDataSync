package com.samsung.health.mobile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.samsung.health.data.TrackedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Collections
import javax.inject.Inject

data class UiState(
    val latestAveragedData: TrackedData? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Thread-safe buffer to hold incoming data records for one second.
    private val dataBuffer = Collections.synchronizedList(mutableListOf<TrackedData>())
    private var tickerJob: Job? = null

    init {
        startDataProcessingTicker()
    }

    // Called from MainActivity with each new data packet from the watch.
    fun onNewDataReceived(data: List<TrackedData>) {
        dataBuffer.addAll(data)
    }

    private fun startDataProcessingTicker() {
        if (tickerJob?.isActive == true) return // Ensure only one ticker is running
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Process data every 1 second
                processAndSaveBufferedData()
            }
        }
    }

    private fun processAndSaveBufferedData() {
        if (dataBuffer.isEmpty()) return

        // Create a snapshot of the buffer and clear the original for the next second.
        val snapshot = synchronized(dataBuffer) {
            val list = ArrayList(dataBuffer)
            dataBuffer.clear()
            list
        }

        // --- AVERAGING LOGIC ---
        val avgHr = snapshot.mapNotNull { it.hr }.average().takeIf { !it.isNaN() }
        val avgSpo2 = snapshot.mapNotNull { it.spo2 }.average().toFloat().takeIf { !it.isNaN() }
        val avgSkinTemp = snapshot.mapNotNull { it.skinTemp }.average().toFloat().takeIf { !it.isNaN() }
        val avgEda = snapshot.mapNotNull { it.eda }.average().toFloat().takeIf { !it.isNaN() }
        val avgEcg = snapshot.mapNotNull { it.ecg }.average().toFloat().takeIf { !it.isNaN() }
        val avgBvp = snapshot.mapNotNull { it.bvp }.average().toFloat().takeIf { !it.isNaN() }
        val avgPpgGreen = snapshot.mapNotNull { it.ppgGreen }.average().toInt().takeIf { it != 0 }
        val avgPpgRed = snapshot.mapNotNull { it.ppgRed }.average().toInt().takeIf { it != 0 }
        val avgPpgIr = snapshot.mapNotNull { it.ppgIr }.average().toInt().takeIf { it != 0 }
        val avgAccX = snapshot.mapNotNull { it.accX }.average().toInt().takeIf { it != 0 }
        val avgAccY = snapshot.mapNotNull { it.accY }.average().toInt().takeIf { it != 0 }
        val avgAccZ = snapshot.mapNotNull { it.accZ }.average().toInt().takeIf { it != 0 }

        val averagedRecord = TrackedData(
            timestamp = System.currentTimeMillis(),
            hr = avgHr?.toInt(),
            spo2 = avgSpo2,
            skinTemp = avgSkinTemp,
            eda = avgEda,
            ecg = avgEcg,
            bvp = avgBvp,
            ppgGreen = avgPpgGreen,
            ppgRed = avgPpgRed,
            ppgIr = avgPpgIr,
            accX = avgAccX,
            accY = avgAccY,
            accZ = avgAccZ,
            // IBI is a list of discrete intervals, so we just take the last valid one.
            ibi = snapshot.lastOrNull()?.ibi ?: arrayListOf()
        )

        _uiState.update { it.copy(latestAveragedData = averagedRecord) }
        saveToFirestore(averagedRecord)
    }

    private fun saveToFirestore(data: TrackedData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Add the data object as a new document to the "healthData" collection
                firestore.collection("healthData").add(data).await()
                Log.d("Firestore", "Successfully saved data: ${data.timestamp}")
            } catch (e: Exception) {
                Log.e("Firestore", "Error saving data", e)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel() // Clean up the coroutine when ViewModel is destroyed
    }
}