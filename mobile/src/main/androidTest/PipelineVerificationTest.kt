package com.samsung.health.mobile.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PipelineVerificationTest {

    @Test
    fun pipeline_matches_python_ground_truth() {

        val batch = TestDataLoader.loadHdBatch()

        val result = runPipeline(batch)

        // Ground truth from Data_derivation_phone.ipynb
        val gtHr = 72.4
        val gtSpo2 = 97.2
        val gtMeanIbi = 828.0

        assertNotNull(result.hrBpm)
        assertNotNull(result.spo2)
        assertTrue(result.ibiMs.isNotEmpty())

        assertTrue(abs(result.hrBpm!! - gtHr) <= 2.0)
        assertTrue(abs(result.spo2!! - gtSpo2) <= 1.5)

        val meanIbi = result.ibiMs.average()
        assertTrue(abs(meanIbi - gtMeanIbi) <= 10.0)

        assertTrue(result.flags.batchValid)
        assertTrue(result.confidence >= 0.85)
    }
}

