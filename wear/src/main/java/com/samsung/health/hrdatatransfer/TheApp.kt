package com.samsung.health.hrdatatransfer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.HiltAndroidApp

// ▼▼▼ NEW CONSTANT ▼▼▼
const val NOTIFICATION_CHANNEL_ID = "HealthTrackingChannel"
// ▲▲▲ END NEW CONSTANT ▲▲▲

@HiltAndroidApp
class TheApp : Application() {

    // ▼▼▼ NEW onCREATE METHOD ▼▼▼
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Health Data Streaming",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    // ▲▲▲ END NEW onCREATE METHOD ▲▲▲
}