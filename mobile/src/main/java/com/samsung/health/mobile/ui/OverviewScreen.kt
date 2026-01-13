package com.samsung.health.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.health.mobile.HealthSnapshot

// --- THEME COLORS ---
private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)

private val StatusGreen = Color(0xFF65D46E)
private val StatusRed = Color(0xFFFF5252)
private val StatusOrange = Color(0xFFFFAB40)
private val StatusBlue = Color(0xFF448AFF)

@Composable
fun OverviewScreen(data: HealthSnapshot) {
    val scrollState = rememberScrollState()

    // HR Stats
    val validHrHistory = data.heartRateHistory.filter { it > 0 }
    val minHr = validHrHistory.minOrNull()?.toInt() ?: 0
    val maxHr = validHrHistory.maxOrNull()?.toInt() ?: 0
    val avgHr = if (validHrHistory.isNotEmpty()) validHrHistory.average().toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Summary",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // 1. HEART RATE
        MetricCard(
            title = "Heart Rate",
            icon = Icons.Rounded.Favorite,
            iconTint = StatusRed,
            value = if (data.heartRate > 0) "${data.heartRate}" else "--",
            unit = "bpm",
            status = if (data.heartRate > 0) calculateStatus(data.heartRate.toFloat(), 60f, 100f) else null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CompactStat(label = "Resting", value = if (minHr > 0) "$minHr" else "--")
                CompactStat(label = "Peak", value = if (maxHr > 0) "$maxHr" else "--")
                CompactStat(label = "Avg", value = if (avgHr > 0) "$avgHr" else "--")
            }
            LineChart(
                points = data.heartRateHistory,
                color = StatusRed,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            )
        }

        // 2. ROW: SpO2 & Movement
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // SpO2 (Uses persisted 'data.spo2' from Repo)
            MetricCard(
                title = "SPO2",
                icon = Icons.Rounded.WaterDrop,
                iconTint = Color(0xFF5AB6F7),
                value = if (data.spo2 > 0) "${data.spo2}" else "--",
                unit = "%",
                status = if (data.spo2 > 0) {
                    if (data.spo2 >= 95) Status("Healthy", StatusGreen) else Status("Low", StatusOrange)
                } else null,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(Modifier.height(8.dp))
                // Only show "Measuring..." if value is actually missing (0)
                val stateText = if (data.spo2 > 0) "Stable" else "Measuring..."
                CompactStat(label = "State", value = stateText)
            }

            // Movement (Human Readable)
            val moveState = when {
                data.accMagnitude < 0.2f -> "Resting"
                data.accMagnitude < 0.8f -> "Moving"
                else -> "Active"
            }
            val moveColor = if (moveState == "Resting") StatusBlue else StatusGreen

            MetricCard(
                title = "Movement",
                icon = Icons.Rounded.DirectionsRun,
                iconTint = moveColor,
                value = moveState, // <--- No more G units in main value
                unit = "",
                status = null,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(Modifier.height(8.dp))
                // Show G value as a small detail
                CompactStat(label = "Intensity", value = "%.2f G".format(data.accMagnitude))
            }
        }

        // 3. ROW: Skin Temp & Resp
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Skin Temp
            MetricCard(
                title = "Temp",
                icon = Icons.Rounded.Thermostat,
                iconTint = Color(0xFFFFCC80),
                value = if (data.skinTemperature > 0) "%.1f".format(data.skinTemperature) else "--",
                unit = "°C",
                status = if (data.skinTemperature > 0) calculateStatus(data.skinTemperature, 30f, 37.5f) else null,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(Modifier.height(8.dp))
                CompactStat(label = "Range", value = "32 - 36°C")
            }

            // Respiration
            MetricCard(
                title = "Resp.",
                icon = Icons.Rounded.Air,
                iconTint = Color(0xFFB39DDB),
                value = if (data.respirationRate > 0) "${data.respirationRate}" else "--",
                unit = "rpm",
                status = if (data.respirationRate > 0) calculateStatus(data.respirationRate.toFloat(), 12f, 20f) else null,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(Modifier.height(8.dp))
                CompactStat(label = "Avg", value = if (data.respirationRate > 0) "12-20" else "--")
            }
        }

        // 4. ECG CARD
        ECGCard(data.ecgSignal)
    }
}

// ... (Rest of UI components: MetricCard, CompactStat, calculateStatus, ECGCard, etc. remain unchanged)
@Composable
fun MetricCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    status: Status? = null,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextSubtle, fontWeight = FontWeight.Medium)
                }
                if (status != null) {
                    Surface(
                        color = status.color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = status.label,
                            color = status.color,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // Main Value Text
                Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = TextWhite)
                if (unit.isNotEmpty() && value != "--" && value != "Resting" && value != "Moving" && value != "Active") {
                    Text(text = " $unit", style = MaterialTheme.typography.bodyMedium, color = TextSubtle, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                }
            }
            if (content != null) content()
        }
    }
}

@Composable
fun CompactStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextWhite, fontWeight = FontWeight.Medium)
    }
}

data class Status(val label: String, val color: Color)

fun calculateStatus(value: Float, min: Float, max: Float): Status {
    return when {
        value < min -> Status("Low", StatusBlue)
        value > max -> Status("High", StatusOrange)
        else -> Status("Healthy", StatusGreen)
    }
}

@Composable
fun ECGCard(ecgData: List<Float>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(bottom = 80.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF263238)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MonitorHeart, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ECG Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextWhite)
                        Text(if (isExpanded) "Tap to hide" else "Tap to view live graph", style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSubtle
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                            .padding(4.dp)
                    ) {
                        GridBackground()
                        LineChart(points = ecgData, color = Color(0xFF00E5FF), strokeWidth = 3f, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun GridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridColor = Color.White.copy(alpha = 0.05f)
        val stepX = size.width / 10
        val stepY = size.height / 5
        for (i in 1..9) drawLine(start = androidx.compose.ui.geometry.Offset(i * stepX, 0f), end = androidx.compose.ui.geometry.Offset(i * stepX, size.height), color = gridColor)
        for (i in 1..4) drawLine(start = androidx.compose.ui.geometry.Offset(0f, i * stepY), end = androidx.compose.ui.geometry.Offset(size.width, i * stepY), color = gridColor)
    }
}

@Composable
fun LineChart(points: List<Float>, color: Color, strokeWidth: Float = 4f, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    Canvas(modifier = modifier) {
        val path = Path()
        val width = size.width
        val height = size.height
        val max = points.maxOrNull() ?: 1f
        val min = points.minOrNull() ?: 0f
        val range = if (max - min == 0f) 1f else max - min

        points.forEachIndexed { index, point ->
            val x = (index.toFloat() / (points.size - 1)) * width
            val y = height - ((point - min) / range) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.2f), Color.Transparent)))
    }
}