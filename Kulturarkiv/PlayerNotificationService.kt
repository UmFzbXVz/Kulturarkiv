package com.example.mediatoolkit

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlayerNotificationService : Service() {override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = PlayerManager.buildNotification(this)

    startForeground(PlayerManager.NOTIFICATION_ID, notification)

    stopForeground(false)
    stopSelf()

    return START_NOT_STICKY
}

    override fun onBind(intent: Intent?): IBinder? = null}

