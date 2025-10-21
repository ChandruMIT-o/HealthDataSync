package com.samsung.health.mobile.presentation.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.health.data.TrackedData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A data class to structure our table rows
private data class TableRowData(val label: String, val value: String)

@Composable
fun DataTable(result: TrackedData) {
    // This logic now conditionally builds the list of rows to display.
    // `remember` re-runs this block only when `result` changes.
    val dataRows = remember(result) {
        // If HR is 0 or null, only show basic data.
        if (result.hr == null || result.hr == 0) {
            buildList {
                add(TableRowData("Timestamp", SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(result.timestamp))))
                add(TableRowData("Heart Rate", "---"))
                result.accX?.let { add(TableRowData("Accel X", it.toString())) }
                result.accY?.let { add(TableRowData("Accel Y", it.toString())) }
                result.accZ?.let { add(TableRowData("Accel Z", it.toString())) }
            }
        } else {
            // Otherwise, show all available data.
            buildList {
                add(TableRowData("Timestamp", SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(result.timestamp))))
                result.hr?.let { add(TableRowData("Heart Rate", "$it bpm")) }
                result.ibi?.let { if (it.isNotEmpty()) add(TableRowData("IBI", it.joinToString())) }
                result.spo2?.let { add(TableRowData("SpO2", "%.1f %%".format(it))) }
                result.skinTemp?.let { add(TableRowData("Skin Temp", "%.2f °C".format(it))) }
                result.eda?.let { add(TableRowData("EDA", "%.3f µS".format(it))) }
                result.ecg?.let { add(TableRowData("ECG (sim)", "%.2f µV".format(it))) }
                result.bvp?.let { add(TableRowData("BVP (sim)", "%.2f".format(it))) }
                result.ppgGreen?.let { add(TableRowData("PPG Green", it.toString())) }
                result.ppgRed?.let { add(TableRowData("PPG Red", it.toString())) }
                result.ppgIr?.let { add(TableRowData("PPG IR", it.toString())) }
                result.accX?.let { add(TableRowData("Accel X", it.toString())) }
                result.accY?.let { add(TableRowData("Accel Y", it.toString())) }
                result.accZ?.let { add(TableRowData("Accel Z", it.toString())) }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            // This modifier animates size changes smoothly
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TableCell(text = "Sensor", weight = 1f, isHeader = true)
                TableCell(text = "Value", weight = 1f, isHeader = true, alignment = TextAlign.End)
            }

            Divider(color = Color.Gray.copy(alpha = 0.3f))

            // Table Body - Replaced LazyColumn with a regular Column
            Column {
                dataRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(text = row.label, weight = 1f)
                        // This will animate the text value changes with a fade
                        Crossfade(targetState = row.value, label = "value-crossfade") { value ->
                            TableCell(text = value, weight = 1f, alignment = TextAlign.End)
                        }
                    }
                    if (dataRows.last() != row) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.Gray.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    alignment: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(vertical = 4.dp),
        color = when {
            isHeader -> Color.White
            text == "---" -> Color.Gray.copy(alpha = 0.7f)
            else -> Color(0xFFE0E0E0)
        },
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (isHeader) 16.sp else 14.sp,
        textAlign = alignment
    )
}

