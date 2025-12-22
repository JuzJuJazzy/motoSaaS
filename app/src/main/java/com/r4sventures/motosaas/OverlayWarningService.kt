package com.r4sventures.motosaas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayWarningService : Service() {

    private var windowManager: WindowManager? = null
    private var warningView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 1) Start Foreground
        createServiceNotificationChannel()
        val notification = NotificationCompat.Builder(this, "overlay_service")
            .setContentTitle("Camera Proximity Active")
            .setContentText("Overlay running in background")
            .setSmallIcon(R.drawable.ic_warning)
            .setOngoing(true)
            .build()
        startForeground(1001, notification)

        // 2) Check permission before adding overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            stopSelf()   // ❗หยุด service ทันที ถ้าไม่มีสิทธิ์
            return
        }

        // 3) Safe to add overlay
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        warningView = inflater.inflate(R.layout.overlay_warning_bar, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        warningView?.visibility = View.GONE
        windowManager?.addView(warningView, params)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW" -> warningView?.visibility = View.VISIBLE
            "HIDE" -> warningView?.visibility = View.GONE
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        warningView?.let { windowManager?.removeView(it) }
        warningView = null
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service",
                "Overlay Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
