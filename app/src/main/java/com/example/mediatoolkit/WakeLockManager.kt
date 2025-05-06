package com.example.mediatoolkit

import android.content.Context
import android.os.PowerManager
import android.util.Log

class WakeLockManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(tag: String = "PlayerActivity:WakeLock", durationMs: Long = 10 * 60 * 1000L) {
        if (wakeLock?.isHeld == true) {
            Log.d("WakeLock", "WakeLock already held, skipping acquire.")
            return
        }

        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            tag
        ).apply {
            try {
                acquire(durationMs)
                Log.d("WakeLock", "WakeLock acquired for $durationMs ms.")
            } catch (e: Exception) {
                Log.e("WakeLock", "Failed to acquire wakelock: ${e.message}")
            }
        }
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.let {
            try {
                it.release()
                Log.d("WakeLock", "WakeLock released.")
            } catch (e: Exception) {
                Log.e("WakeLock", "Failed to release wakelock: ${e.message}")
            }
        }
        wakeLock = null
    }
}
