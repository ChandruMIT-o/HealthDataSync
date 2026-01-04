package com.samsung.health.hrdatatransfer.data

import com.samsung.health.hrdatatransfer.data.model.RawSensorBatch
import java.nio.ByteBuffer

object BatchSerializer {

    // Header byte to identify format version (optional but good practice)
    private const val VERSION: Byte = 1

    fun toBytes(batch: RawSensorBatch): ByteArray {
        // Calculate exact size needed to avoid resizing
        val ppgCount = batch.ppgGreen.size
        val accCount = batch.accX.size
        val tempCount = batch.skinTemp.size

        // 4 bytes per Float, 8 bytes per Long, 4 bytes per Int size header
        val totalSize = 1 + // Version
                8 + 4 + // BatchId, Duration
                8 + 4 + (ppgCount * 4 * 3) + // PPG Ts, Count, 3 Arrays
                8 + 4 + (accCount * 4 * 3) + // Acc Ts, Count, 3 Arrays
                8 + 4 + (tempCount * 4)      // Temp Ts, Count, 1 Array

        val buffer = ByteBuffer.allocate(totalSize)

        buffer.put(VERSION)
        buffer.putLong(batch.batchId)
        buffer.putInt(batch.durationMs)

        // PPG
        buffer.putLong(batch.ppgTimestamp)
        buffer.putInt(ppgCount)
        for(f in batch.ppgGreen) buffer.putFloat(f)
        for(f in batch.ppgRed) buffer.putFloat(f)
        for(f in batch.ppgIr) buffer.putFloat(f)

        // ACC
        buffer.putLong(batch.accTimestamp)
        buffer.putInt(accCount)
        for(f in batch.accX) buffer.putFloat(f)
        for(f in batch.accY) buffer.putFloat(f)
        for(f in batch.accZ) buffer.putFloat(f)

        // TEMP
        buffer.putLong(batch.tempTimestamp)
        buffer.putInt(tempCount)
        for(f in batch.skinTemp) buffer.putFloat(f)

        return buffer.array()
    }
}