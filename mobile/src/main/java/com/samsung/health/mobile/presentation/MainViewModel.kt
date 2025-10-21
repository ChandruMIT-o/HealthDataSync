package com.samsung.health.mobile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DatabaseReference //  MODIFIED: Import changed
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
    private val databaseReference: DatabaseReference //  MODIFIED: Injected DatabaseReference
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val dataBuffer = Collections.synchronizedList(mutableListOf<TrackedData>())
    private var tickerJob: Job? = null

    init {
        startDataProcessingTicker()
    }

    // This function remained unchanged
    fun onNewDataReceived(data: List<TrackedData>) {
        dataBuffer.addAll(data)
    }

    // This function remained unchanged
    private fun startDataProcessingTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                processAndSaveBufferedData()
            }
        }
    }

    private fun processAndSaveBufferedData() {
        if (dataBuffer.isEmpty()) return

        // This snapshot and averaging logic remained unchanged
        val snapshot = synchronized(dataBuffer) {
            val list = ArrayList(dataBuffer)
            dataBuffer.clear()
            list
        }

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
            ibi = snapshot.lastOrNull()?.ibi ?: arrayListOf()
        )

        _uiState.update { it.copy(latestAveragedData = averagedRecord) }

        //  MODIFIED: Call to the new save function
        saveToRealtimeDatabase(averagedRecord)
    }

    //  REPLACED: This function replaced the old saveToFirestore()
    private fun saveToRealtimeDatabase(data: TrackedData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // .push() creates a new unique, time-stamped key for each entry.
                // .setValue() writes the data object to that new key.
                databaseReference.push().setValue(data).await()
                Log.d("RTDB", "Successfully saved data: ${data.timestamp}")
            } catch (e: Exception) {
                Log.e("RTDB", "Error saving data", e)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}