package com.samsung.health.mobile.pipeline

import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader

object TestDataLoader {

    fun loadHdBatch(): RawSensorBatch {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val reader = BufferedReader(InputStreamReader(ctx.assets.open("HD.csv")))

        val header = reader.readLine().split(",")

        val green = FloatArray(1500)
        val red = FloatArray(1500)
        val ir = FloatArray(1500)
        val ax = FloatArray(1500)
        val ay = FloatArray(1500)
        val az = FloatArray(1500)
        val temp = FloatArray(1500)

        var i = 0
        reader.forEachLine {
            val c = it.split(",")
            green[i] = c[header.indexOf("ppg_green")].toFloat()
            red[i] = c[header.indexOf("ppg_red")].toFloat()
            ir[i] = c[header.indexOf("ppg_ir")].toFloat()
            ax[i] = c[header.indexOf("acc_x")].toFloat()
            ay[i] = c[header.indexOf("acc_y")].toFloat()
            az[i] = c[header.indexOf("acc_z")].toFloat()
            temp[i] = c[header.indexOf("skin_temp")].toFloat()
            i++
        }

        return RawSensorBatch(
            batchId = 42L,
            ppgTimestamp = 0L,
            ppgGreen = green,
            ppgRed = red,
            ppgIr = ir,
            accTimestamp = 0L,
            accX = ax,
            accY = ay,
            accZ = az,
            tempTimestamp = 0L,
            skinTemp = temp
        )
    }
}

