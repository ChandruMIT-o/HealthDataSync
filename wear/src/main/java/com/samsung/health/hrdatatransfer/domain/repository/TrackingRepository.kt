package com.samsung.health.hrdatatransfer.domain.repository

import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch // Import NEW model
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    // Change return type from HealthDataRecord to RawSensorBatch
    fun track(trackerTypes: Set<HealthTrackerType>): Flow<RawSensorBatch>

    fun stopTracking()
    fun flushTrackers()
}