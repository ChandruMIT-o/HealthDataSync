// --- src/main/java/com/samsung/health/mobile/ui/SleepAnalysisScreen.kt ---
package com.samsung.health.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SamsungBlack = Color(0xFF000000)
private val DeepSleepColor = Color(0xFF673AB7)
private val LightSleepColor = Color(0xFF03A9F4)
private val REMColor = Color(0xFFFFAB40)
private val AwakeColor = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepAnalysisScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deep Sleep Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SamsungBlack,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = SamsungBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Sleep Score", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("85", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Good", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF65D46E))

                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SleepStat("Duration", "7h 12m")
                        SleepStat("Deep", "1h 45m")
                        SleepStat("REM", "1h 30m")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Sleep Stages (Hypnogram)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            // Hypnogram Graph Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Hypnogram()
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LegendItem(AwakeColor, "Awake")
                LegendItem(REMColor, "REM")
                LegendItem(LightSleepColor, "Light")
                LegendItem(DeepSleepColor, "Deep")
            }
        }
    }
}

@Composable
fun SleepStat(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun Hypnogram() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Y-Levels for stages
        val yAwake = h * 0.1f
        val yRem = h * 0.35f
        val yLight = h * 0.6f
        val yDeep = h * 0.9f

        // Fake Hypnogram Data (Time 0-100%)
        // 0=Awake, 1=REM, 2=Light, 3=Deep
        val stages = listOf(
            0 to yAwake, 10 to yLight, 20 to yDeep, 35 to yLight,
            40 to yRem, 50 to yLight, 65 to yDeep, 80 to yLight,
            90 to yRem, 100 to yAwake
        )

        val path = Path()
        path.moveTo(0f, yAwake)

        var prevX = 0f
        var prevY = yAwake

        stages.forEach { (percent, yLevel) ->
            val x = (percent / 100f) * w
            // Step Chart Logic: Horizontal line to new X, then Vertical line to new Y
            path.lineTo(x, prevY)
            path.lineTo(x, yLevel)

            prevX = x
            prevY = yLevel
        }

        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.8f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}