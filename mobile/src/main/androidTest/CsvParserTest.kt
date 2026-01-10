package com.samsung.health.mobile.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class CsvParserTest {

    @Test
    fun parseHdCsv_correctly() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val input = ctx.assets.open("HD.csv")

        val reader = BufferedReader(InputStreamReader(input))
        val header = reader.readLine().split(",")

        val ppgGreen = FloatArray(1500)
        val ppgRed = FloatArray(1500)
        val ppgIr = FloatArray(1500)
        val accX = FloatArray(1500)
        val accY = FloatArray(1500)
        val accZ = FloatArray(1500)
        val skinTemp = FloatArray(1500)

        var idx = 0
        reader.forEachLine { line ->
            val cols = line.split(",")

            ppgGreen[idx] = cols[header.indexOf("ppg_green")].toFloat()
            ppgRed[idx]   = cols[header.indexOf("ppg_red")].toFloat()
            ppgIr[idx]    = cols[header.indexOf("ppg_ir")].toFloat()
            accX[idx]     = cols[header.indexOf("acc_x")].toFloat()
            accY[idx]     = cols[header.indexOf("acc_y")].toFloat()
            accZ[idx]     = cols[header.indexOf("acc_z")].toFloat()
            skinTemp[idx] = cols[header.indexOf("skin_temp")].toFloat()
            idx++
        }

        assertEquals(1500, idx)

        val batch = RawSensorBatch(
            batchId = 1L,
            ppgTimestamp = 0L,
            ppgGreen = ppgGreen,
            ppgRed = ppgRed,
            ppgIr = ppgIr,
            accTimestamp = 0L,
            accX = accX,
            accY = accY,
            accZ = accZ,
            tempTimestamp = 0L,
            skinTemp = skinTemp
        )

        assertEquals(1500, batch.ppgGreen.size)
    }
}

