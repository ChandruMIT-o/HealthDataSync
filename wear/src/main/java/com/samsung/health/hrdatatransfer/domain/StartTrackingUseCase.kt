package com.samsung.health.hrdatatransfer.domain

import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.data.HealthDataRecord
import com.samsung.health.hrdatatransfer.data.TrackingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartTrackingUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository
) {
    // This now calls the 'track' method which returns the Flow
    operator fun invoke(trackerTypes: Set<HealthTrackerType>): Flow<HealthDataRecord> {
        return trackingRepository.track(trackerTypes)
    }
}