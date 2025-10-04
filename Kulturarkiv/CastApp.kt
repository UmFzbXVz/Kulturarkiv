package com.example.mediatoolkit

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

class CastApp : Application(), OptionsProvider {
    override fun onCreate() {
        super.onCreate()
        CastContext.getSharedInstance(this)
    }override fun getCastOptions(context: android.content.Context): CastOptions {
        val mediaOptions = CastMediaOptions.Builder()
            .setExpandedControllerActivityClassName(ExpandedControllerActivity::class.java.name)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: android.content.Context): List<SessionProvider>? {
        return null
    }

    companion object {
        private const val DEFAULT_RECEIVER_APP_ID = "CC1AD845"
    }}