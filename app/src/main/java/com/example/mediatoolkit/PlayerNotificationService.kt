package com.example.mediatoolkit

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlayerNotificationService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = PlayerManager.buildNotification(this)

        startForeground(PlayerManager.NOTIFICATION_ID, notification)

        // Hvis vi ved, at notifikationen allerede er håndteret i PlayerNotificationManager,
        // og vi kun bruger service til at sætte den i gang:
        stopForeground(false) // behold notifikationen
        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
