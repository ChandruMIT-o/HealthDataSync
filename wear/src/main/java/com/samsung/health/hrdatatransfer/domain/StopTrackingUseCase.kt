package com.samsung.health.hrdatatransfer.domain

import com.samsung.health.hrdatatransfer.data.TrackingRepository
import javax.inject.Inject

class StopTrackingUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository
) {
    operator fun invoke() {
        trackingRepository.stopTracking()
    }
}