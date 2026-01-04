package com.samsung.health.hrdatatransfer.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch // Import NEW model
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainViewModel"

data class UiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isTracking: Boolean = false,
    val latestData: RawSensorBatch? = null, // CHANGE HERE
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
    private val trackingStateHolder: TrackingStateHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            trackingStateHolder.trackingState.collect { trackingState ->
                _uiState.value = mapTrackingStateToUiState(trackingState)
            }
        }
    }

    private fun mapTrackingStateToUiState(trackingState: TrackingState): UiState {
        return when (trackingState) {
            is TrackingState.Connected -> UiState(
                connectionState = ConnectionState.Connected,
                isTracking = trackingState.isTracking,
                latestData = trackingState.latestData
            )
            is TrackingState.Connecting -> UiState(connectionState = ConnectionState.Connecting)
            is TrackingState.Disconnected -> UiState(connectionState = ConnectionState.Disconnected)
            is TrackingState.Failed -> UiState(
                connectionState = ConnectionState.Failed,
                connectionException = trackingState.exception
            )
        }
    }
}