// --- src/main/java/com/samsung/health/mobile/ui/InsightsScreen.kt ---
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

// --- THEME ---
private val SamsungBlack = Color(0xFF000000)
private val SamsungDarkCard = Color(0xFF1C1C1E)
private val TextWhite = Color(0xFFFAFAFA)
private val TextSubtle = Color(0xFF9E9E9E)
private val DeepSleepPurple = Color(0xFF673AB7)

@Composable
fun InsightsScreen(data: HealthSnapshot, onDeepSleepClick: () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SamsungBlack)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Advanced Analysis",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // 1. Sleep Quality Index (SQI)
        IndexCard(
            title = "Sleep Quality (SQI)",
            icon = Icons.Rounded.Bedtime,
            value = data.sqi,
            history = data.sqiHistory,
            description = "Sleep efficiency based on movement and HRV."
        )

        // 2. Psychosomatic Stress (PSI)
        IndexCard(
            title = "Stress Index (PSI)",
            icon = Icons.Rounded.Psychology,
            value = data.psi,
            history = data.psiHistory,
            description = "Derived from HRV & skin conductance.",
            inverse = true // Lower is better for stress
        )

        // 3. Cardiovascular Health (CVHS)
        IndexCard(
            title = "Cardio Health (CVHS)",
            icon = Icons.Rounded.Favorite,
            value = data.cvhs,
            history = data.cvhsHistory,
            description = "Composite score of HR, BP est., and SpO2."
        )

        // 4. Cognitive Load (CLS)
        IndexCard(
            title = "Cognitive Load (CLS)",
            icon = Icons.Rounded.Memory,
            value = data.cls,
            history = data.clsHistory,
            description = "Mental fatigue estimation.",
            inverse = true
        )

        // 5. Emotional Vitality (EVS)
        IndexCard(
            title = "Emotional Vitality (EVS)",
            icon = Icons.Rounded.Mood,
            value = data.evs,
            history = data.evsHistory,
            description = "Resilience score based on autonomic balance."
        )

        Spacer(Modifier.height(16.dp))

        // Deep Sleep Button
        Button(
            onClick = onDeepSleepClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepSleepPurple)
        ) {
            Icon(Icons.Rounded.NightsStay, null, tint = TextWhite)
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text("Deep Sleep Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("View hypnogram and stages", fontSize = 12.sp, color = TextWhite.copy(0.7f))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowForwardIos, null, tint = TextWhite.copy(0.7f), modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun IndexCard(
    title: String,
    icon: ImageVector,
    value: Float, // 0.0 to 1.0
    history: List<Float>,
    description: String,
    inverse: Boolean = false // If true, low score is GOOD (like Stress)
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Status Logic
    val status = if (inverse) {
        when {
            value < 0.3f -> Status("Optimal", Color(0xFF65D46E)) // Green
            value < 0.7f -> Status("Moderate", Color(0xFFFFAB40)) // Orange
            else -> Status("High", Color(0xFFFF5252)) // Red
        }
    } else {
        when {
            value > 0.7f -> Status("Excellent", Color(0xFF65D46E)) // Green
            value > 0.4f -> Status("Average", Color(0xFFFFAB40)) // Orange
            else -> Status("Poor", Color(0xFFFF5252)) // Red
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SamsungDarkCard),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = status.color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextWhite)
                        Text("%.2f / 1.0".format(value), style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                    }
                }

                // Badge
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

            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    Spacer(Modifier.height(16.dp))

                    Text(description, style = MaterialTheme.typography.bodySmall, color = TextSubtle)
                    Spacer(Modifier.height(16.dp))

                    Text("Hourly Trend", style = MaterialTheme.typography.labelSmall, color = TextWhite)
                    Spacer(Modifier.height(8.dp))

                    // Colored Range Plot
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        ScoreLineChart(points = history, color = status.color)
                    }
                }
            }
        }
    }
}

// Re-using chart logic with minor tweaks for 0-1 range
@Composable
fun ScoreLineChart(points: List<Float>, color: Color) {
    if (points.isEmpty()) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        val width = size.width
        val height = size.height
        // Fixed range 0.0 to 1.0
        val max = 1.0f
        val min = 0.0f

        points.forEachIndexed { index, point ->
            val x = (index.toFloat() / (points.size - 1)) * width
            val y = height - ((point - min) / (max - min)) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Gradient Fill
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
            )
        )
    }
}