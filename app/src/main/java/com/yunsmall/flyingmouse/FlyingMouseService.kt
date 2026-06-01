package com.yunsmall.flyingmouse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class FlyingMouseService : Service() {

    companion object {
        private const val TAG = "FlyingMouseService"
        private const val CHANNEL_ID = "flyingmouse_service"
        private const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): FlyingMouseService = this@FlyingMouseService
    }

    private val binder = LocalBinder()

    @Volatile var serverRunning: Boolean = false
        private set
    @Volatile var port: Int = 3240
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        FlyingMouseNative.init()
        FlyingMouseNative.createServer()
        Log.i(TAG, "Service created, server ready")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        FlyingMouseNative.destroyServer()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    fun startServer(port: Int): Boolean {
        if (serverRunning) return true
        val ok = FlyingMouseNative.startServer(port)
        if (ok) { this.port = port; serverRunning = true }
        updateNotification()
        return ok
    }

    fun stopServer() {
        if (!serverRunning) return
        FlyingMouseNative.stopServer()
        serverRunning = false
        updateNotification()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlyingMouse")
            .setContentText(if (serverRunning) "Server running on port $port" else "Server stopped")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "FlyingMouse Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
