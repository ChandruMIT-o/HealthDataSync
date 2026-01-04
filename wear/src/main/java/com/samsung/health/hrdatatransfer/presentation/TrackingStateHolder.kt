package com.samsung.health.hrdatatransfer.presentation

import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch // Import NEW model
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingStateHolder @Inject constructor() {
    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Disconnected)
    val trackingState = _trackingState.asStateFlow()

    fun setTrackingState(state: TrackingState) {
        _trackingState.value = state
    }
}

sealed class TrackingState {
    object Disconnected : TrackingState()
    object Connecting : TrackingState()

    // UPDATE THIS LINE:
    data class Connected(
        val isTracking: Boolean = false,
        val latestData: RawSensorBatch? = null
    ) : TrackingState()

    data class Failed(val exception: HealthTrackerException?) : TrackingState()
}