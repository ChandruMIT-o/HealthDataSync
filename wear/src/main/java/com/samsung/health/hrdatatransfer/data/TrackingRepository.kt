package com.samsung.health.hrdatatransfer.data

import com.samsung.android.service.health.tracking.data.HealthTrackerType
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    // The getAvailableTrackers function is now GONE.
    fun track(trackerTypes: Set<HealthTrackerType>): Flow<HealthDataRecord>
    fun stopTracking()
}