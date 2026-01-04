package com.samsung.health.hrdatatransfer.domain

import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch // Import NEW model
import com.samsung.health.hrdatatransfer.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StartTrackingUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository
) {
    // Change return type here too
    operator fun invoke(trackerTypes: Set<HealthTrackerType>): Flow<RawSensorBatch> {
        return trackingRepository.track(trackerTypes)
    }
}