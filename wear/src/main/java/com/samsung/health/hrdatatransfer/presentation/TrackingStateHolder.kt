package com.samsung.health.hrdatatransfer.presentation

import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.health.hrdatatransfer.data.HealthDataRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton holder for the app's tracking state.
 * This allows the ForegroundService to report its state and data,
 * and the ViewModel to observe it, without them being directly bound.
 */
@Singleton
class TrackingStateHolder @Inject constructor() {

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Disconnected)
    val trackingState = _trackingState.asStateFlow()

    fun setTrackingState(state: TrackingState) {
        _trackingState.value = state
    }
}

/**
 * Represents the complete state of the tracking service.
 */
sealed class TrackingState {
    object Disconnected : TrackingState()
    object Connecting : TrackingState()
    data class Connected(val isTracking: Boolean = false, val latestData: HealthDataRecord? = null) : TrackingState()
    data class Failed(val exception: HealthTrackerException?) : TrackingState()
}