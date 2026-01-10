package com.samsung.health.mobile.pipeline

fun runPipeline(batch: RawSensorBatch): DerivedMetrics {

    val (hr, peaks) = HeartRateEstimator.estimate(batch.ppgGreen)

    val ibi = IBIEstimator.estimate(peaks)
    val spo2 = SpO2Estimator.estimate(batch.ppgRed, batch.ppgIr)
    val rr = RespirationEstimator.estimate(batch.ppgGreen, peaks)

    val ibiCoverage = ibi.size.toDouble() / peaks.size.coerceAtLeast(1)

    val flags = ValidationFlags(
        ppgOk = true,          // Raw PPG already gated earlier
        bvpOk = peaks.size in 40..120,
        hrOk = hr != null,
        ibiOk = ibiCoverage >= 0.8,
        rrOk = rr != null,
        batchValid = hr != null && ibiCoverage >= 0.8
    )

    val confidence = ValidationUtils.confidence(
        flags.hrOk,
        ibiCoverage,
        flags.rrOk
    )

    return DerivedMetrics(
        hrBpm = hr,
        spo2 = spo2,
        rrBpm = rr,
        ibiMs = ibi,
        flags = flags,
        confidence = confidence
    )
}

