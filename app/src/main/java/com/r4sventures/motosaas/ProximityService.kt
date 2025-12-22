package com.r4sventures.motosaas

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProximityService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceNotification()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "ProximityAlert"
            val descriptionText = "Notifications for objects approaching"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("proximity_channel", name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, "proximity_channel")
            .setSmallIcon(R.drawable.ic_warning) // ใส่ไฟล์นี้ใน res/drawable
            .setContentTitle("Proximity Alert Active")
            .setContentText("Detecting Object Approach")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        startForeground(1, notification)
    }
}
