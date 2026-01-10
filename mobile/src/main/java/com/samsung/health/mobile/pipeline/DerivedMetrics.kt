package com.samsung.health.mobile.pipeline

data class DerivedMetrics(
    val hrBpm: Double?,
    val spo2: Double?,
    val rrBpm: Double?,
    val ibiMs: List<Long>,
    val flags: ValidationFlags,
    val confidence: Double
)

data class ValidationFlags(
    val ppgOk: Boolean,
    val bvpOk: Boolean,
    val hrOk: Boolean,
    val ibiOk: Boolean,
    val rrOk: Boolean,
    val batchValid: Boolean
)

