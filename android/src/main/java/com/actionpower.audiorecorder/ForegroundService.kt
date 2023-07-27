package com.actionpower.audiorecorder

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "primary_notification_channel"
        // Service Actions
        const val START = "START"

        // Intent Extras
        const val STOPWATCH_ACTION = "STOPWATCH_ACTION"
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()

        return START_NOT_STICKY
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Recording notification",
                    NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = 1
            notificationChannel.enableVibration(false)
            notificationChannel.description = "Recording 알람"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val packageName = applicationContext.packageName
        var openApp = applicationContext.packageManager.getLaunchIntentForPackage(packageName)

        if (openApp == null) {
            openApp = Intent()
            openApp.setPackage(packageName)
            openApp.addCategory(Intent.CATEGORY_LAUNCHER)
        }

        openApp.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(applicationContext, 0,
                openApp, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#007EFF"))
                .setContentTitle("daglo")
                .setContentText("녹음 중")
                .setContentIntent(pendingIntent)
                .build()

        startForeground(10, notification)
    }

    override fun onDestroy() {
        stopForeground(true)
        stopSelf()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?){
        stopForeground(true)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}