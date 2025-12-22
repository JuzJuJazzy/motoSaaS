package com.r4sventures.motosaas

import android.content.Context
import android.content.Intent
import android.os.Build

object OverlayController {

    // บังคับให้ service เริ่มก่อน ส่ง action SHOW/HIDE
    private fun ensureServiceStarted(context: Context) {
        val startIntent = Intent(context, OverlayWarningService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }
    }

    fun show(context: Context) {
        ensureServiceStarted(context)

        val intent = Intent(context, OverlayWarningService::class.java)
        intent.action = "SHOW"
        context.startService(intent)
    }

    fun hide(context: Context) {
        ensureServiceStarted(context)

        val intent = Intent(context, OverlayWarningService::class.java)
        intent.action = "HIDE"
        context.startService(intent)
    }
}
