package com.samsung.health.hrdatatransfer.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Node
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.health.hrdatatransfer.data.CapabilityRepository
import com.samsung.health.hrdatatransfer.data.ConnectionMessage
import com.samsung.health.hrdatatransfer.data.HealthDataRecord
import com.samsung.health.hrdatatransfer.data.HealthTrackingServiceConnection
import com.samsung.health.hrdatatransfer.data.MessageRepository
import com.samsung.health.hrdatatransfer.domain.StartTrackingUseCase
import com.samsung.health.hrdatatransfer.domain.StopTrackingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "MainViewModel"
private const val MESSAGE_PATH = "/msg"
private const val CAPABILITY_WEAR_APP = "wear"

// REMOVED bufferedData from UiState
data class UiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val availableSensors: List<HealthTrackerType> = listOf(
        HealthTrackerType.ACCELEROMETER_CONTINUOUS,
        HealthTrackerType.HEART_RATE_CONTINUOUS,
        HealthTrackerType.PPG_CONTINUOUS,
        HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS,
        HealthTrackerType.EDA_CONTINUOUS
    ),
    val selectedSensors: Set<HealthTrackerType> = emptySet(),
    val isTracking: Boolean = false,
    val latestData: HealthDataRecord? = null,
    val connectionException: HealthTrackerException? = null
)

enum class ConnectionState {
    Connecting,
    Connected,
    Disconnected,
    Failed
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val healthTrackingServiceConnection: HealthTrackingServiceConnection,
    private val startTrackingUseCase: StartTrackingUseCase,
    private val stopTrackingUseCase: StopTrackingUseCase,
    private val messageRepository: MessageRepository,
    private val capabilityRepository: CapabilityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var connectionJob: Job? = null
    private var phoneNode: Node? = null

    init {
        setUpTracking()
    }

    fun setUpTracking() {
        if (connectionJob?.isActive == true || _uiState.value.connectionState == ConnectionState.Connected) {
            return
        }
        connectionJob = viewModelScope.launch {
            // Find the phone node ahead of time
            findPhoneNode()

            _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
            healthTrackingServiceConnection.connectionFlow.collect { message ->
                when (message) {
                    is ConnectionMessage.ConnectionSuccessMessage -> {
                        Log.i(TAG, "Connection Success.")
                        _uiState.update { it.copy(connectionState = ConnectionState.Connected) }
                    }
                    is ConnectionMessage.ConnectionFailedMessage -> {
                        Log.e(TAG, "Connection Failed", message.exception)
                        _uiState.update { it.copy(connectionState = ConnectionState.Failed, connectionException = message.exception) }
                    }
                    is ConnectionMessage.ConnectionEndedMessage -> {
                        Log.w(TAG, "Connection Ended.")
                        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                    }
                }
            }
        }
    }

    private suspend fun findPhoneNode() {
        try {
            val nodes = capabilityRepository.getCapabilitiesForReachableNodes()
                .filterValues { CAPABILITY_WEAR_APP in it }
                .keys
            if (nodes.isEmpty()) {
                Log.w(TAG, "No phone node found with 'wear' capability.")
                phoneNode = null
            } else {
                phoneNode = nodes.first()
                Log.i(TAG, "Phone node found: ${phoneNode?.displayName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding phone node", e)
        }
    }

    fun toggleTracker(trackerType: HealthTrackerType) {
        _uiState.update { currentState ->
            val newSelected = currentState.selectedSensors.toMutableSet()
            if (newSelected.contains(trackerType)) newSelected.remove(trackerType) else newSelected.add(trackerType)
            currentState.copy(selectedSensors = newSelected)
        }
    }

    fun startTracking() {
        if (_uiState.value.selectedSensors.isEmpty()) return
        trackingJob?.cancel()
        _uiState.update { it.copy(isTracking = true) }

        trackingJob = startTrackingUseCase(_uiState.value.selectedSensors)
            .onEach { record ->
                // Update the local UI
                _uiState.update { it.copy(latestData = record) }
                // Stream the data to the phone
                sendDataRecord(record)
            }
            .catch { e -> Log.e(TAG, "Tracking Flow error", e) }
            .launchIn(viewModelScope)
    }

    private fun sendDataRecord(record: HealthDataRecord) {
        viewModelScope.launch {
            if (phoneNode == null) {
                // Attempt to find the node again if it wasn't found on startup
                findPhoneNode()
                if (phoneNode == null) {
                    Log.e(TAG, "Cannot send data: phone node is not available.")
                    return@launch
                }
            }
            try {
                // We send a list containing a single record to keep the mobile app's decoding logic simple
                val dataList = listOf(record)
                val jsonString = Json.encodeToString(dataList)
                messageRepository.sendMessage(jsonString, phoneNode!!, MESSAGE_PATH)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data record", e)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        stopTrackingUseCase()
        _uiState.update { it.copy(isTracking = false, latestData = null) }
    }
}