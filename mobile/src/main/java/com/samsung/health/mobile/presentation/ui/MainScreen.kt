package com.samsung.health.mobile.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.health.data.TrackedData
import com.samsung.health.mobile.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.animation.core.*

@Composable
fun MainScreen(
    result: TrackedData?,
    isSaving: Boolean
) {
    var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showIndicator by remember { mutableStateOf(false) }

    // Update timestamp whenever saving happens
    LaunchedEffect(isSaving) {
        if (isSaving) {
            lastUpdateTime = System.currentTimeMillis()
            showIndicator = true
        }
    }

    // Monitor for inactivity to auto-hide after 3 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            val elapsed = System.currentTimeMillis() - lastUpdateTime
            if (elapsed > 3000) showIndicator = false
            delay(500)
        }
    }

    // Pulse animation while visible
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2D3436), Color(0xFF000000)),
                    radius = 800f
                )
            )
    ) {
        // --- Main Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MonitorHeart,
                    contentDescription = "Heart Monitor Icon",
                    tint = Color(0xFFE84393),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(24.dp))

            if (result != null) {
                DataTable(result)
            } else {
                Text(
                    text = stringResource(id = R.string.waiting_for_data),
                    fontSize = 18.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        }

        // --- Smart persistent indicator ---
        AnimatedVisibility(
            visible = showIndicator,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF00B894).copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .alpha(pulseAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudUpload,
                    contentDescription = "Saving to Firebase",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Syncing live data...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
