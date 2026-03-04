package com.samsung.health.mobile.ui

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

private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)

enum class MetricType(val id: String, val label: String, val icon: ImageVector, val color: Color) {
    SQI("SQI", "Sleep Quality", Icons.Rounded.Bedtime, Color(0xFF6366F1)),
    PSI("PSI", "Stress Index", Icons.Rounded.LocalFireDepartment, Color(0xFFF43F5E)),
    CLS("CLS", "Cognitive Load", Icons.Rounded.Psychology, Color(0xFFEAB308)),
    CVHS("CVHS", "Cardio Health", Icons.Rounded.Favorite, Color(0xFF10B981)),
    EVS("EVS", "Vitality", Icons.Rounded.Bolt, Color(0xFFA855F7))
}

@Composable
fun CausalReportScreen(data: HealthSnapshot) {
    var selectedMetric by remember { mutableStateOf(MetricType.SQI) }
    val scrollState = rememberScrollState()

    // Determine current value and history based on selection
    val (currentValue, history) = when (selectedMetric) {
        MetricType.SQI -> data.sqi to data.sqiHistory
        MetricType.PSI -> data.psi to data.psiHistory
        MetricType.CLS -> data.cls to data.clsHistory
        MetricType.CVHS -> data.cvhs to data.cvhsHistory
        MetricType.EVS -> data.evs to data.evsHistory
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Causal Intelligence",
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ST-GAT Neural Analysis",
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Rounded.AutoGraph, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }

        // --- SELECTOR BUTTONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricType.values().forEach { metric ->
                val isSelected = selectedMetric == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) metric.color else SamsungDarkCard)
                        .clickable { selectedMetric = metric },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = metric.icon,
                        contentDescription = metric.label,
                        tint = if (isSelected) Color.White else TextSubtle,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // --- MAIN PANEL ---
        Card(
            colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Diagnostic Snapshot Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Surface(
                            color = Color(0xFF2C2C2E),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Diagnostic View",
                                color = TextSubtle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = selectedMetric.label,
                            color = TextWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "%.1f".format(currentValue),
                        color = selectedMetric.color,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("Historical Trend", color = TextSubtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // The Real-Time Line Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    if (history.isEmpty()) {
                        Text(
                            "Waiting for cloud batch...",
                            color = TextSubtle,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        HistoricalLineChart(points = history, color = selectedMetric.color)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // --- PLACEHOLDERS FOR FUTURE LINKING ---

                Text("Causal Impact Hierarchy", color = TextSubtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // Mock Impactor Card (Will be populated via Firebase Later)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Link, null, tint = TextWhite, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Primary Impactor", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Awaiting Deep Analysis...", color = TextSubtle, fontSize = 10.sp)
                        }
                    }
                    Icon(Icons.Rounded.DataUsage, null, tint = TextSubtle)
                }

                Spacer(Modifier.height(16.dp))

                // Mock AI Inference Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = selectedMetric.color.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Psychology, null, tint = selectedMetric.color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI Logic Synthesis", color = selectedMetric.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Fetching ST-GAT narrative inference for ${selectedMetric.id}. " +
                                    "This section will populate with natural language causations once linked to the Firestore report generation node.",
                            color = TextWhite.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalLineChart(points: List<Float>, color: Color, strokeWidth: Float = 5f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        val width = size.width
        val height = size.height

        // Dynamically scale based on values, but default to 0-100 typical range
        val max = points.maxOrNull()?.coerceAtLeast(10f) ?: 100f
        val min = points.minOrNull()?.coerceAtMost(0f) ?: 0f
        val range = if (max - min == 0f) 1f else max - min

        points.forEachIndexed { index, point ->
            val x = (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * width
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

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.4f), Color.Transparent)
            )
        )
    }
}