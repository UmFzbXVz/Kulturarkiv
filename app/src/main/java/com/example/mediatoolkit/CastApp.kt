package com.example.mediatoolkit

import android.app.Application
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

class CastApp : Application(), OptionsProvider {
    override fun onCreate() {
        super.onCreate()
        // Init CastContext – sørger for discovery starter rigtigt
        CastContext.getSharedInstance(this).addCastStateListener { castState ->
            when (castState) {
                CastState.NO_DEVICES_AVAILABLE -> Log.d("CastApp", "No devices available")
                CastState.CONNECTED -> Log.d("CastApp", "Device connected")
                else -> Log.d("CastApp", "Cast state: $castState")
            }
        }
        Log.d("CastApp", "CastContext Initialized: ${true}")
    }

    override fun getCastOptions(context: android.content.Context): CastOptions {
        val mediaOptions = CastMediaOptions.Builder()
            .setExpandedControllerActivityClassName(ExpandedControllerActivity::class.java.name)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_RECEIVER_APP_ID) // Standard Cast receiver
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: android.content.Context): List<SessionProvider>? {
        // Optional: return custom session providers if needed
        return null
    }

    companion object {
        // Brug den officielle default media receiver fra Google
        private const val DEFAULT_RECEIVER_APP_ID = "CC1AD845"
    }
}