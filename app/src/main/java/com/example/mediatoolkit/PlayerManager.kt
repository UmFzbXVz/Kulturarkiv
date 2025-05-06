package com.example.mediatoolkit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.C.WAKE_MODE_LOCAL
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator

object PlayerManager {

    private var player: ExoPlayer? = null
    private var currentMediaUrl: String? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionConnector: MediaSessionConnector? = null
    private var notificationManager: PlayerNotificationManager? = null
    private var searchResult: SearchResult? = null

    private const val CHANNEL_ID = "media_playback_channel"
    internal const val NOTIFICATION_ID = 1

    // Singleton - initialiserer ExoPlayer hvis ikke allerede initialiseret
    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            val appContext = context.applicationContext
            player = ExoPlayer.Builder(appContext)
                .setWakeMode(WAKE_MODE_LOCAL)
                .build()
            initMediaSession(appContext, player!!)

            // Tilføj lytter som trigger notifikation først ved reel afspilning
            player!!.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("PlayerManager", "onIsPlayingChanged: $isPlaying")
                    if (isPlaying && notificationManager == null) {
                        initNotification(appContext)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> Log.d("PlayerManager", "Afspiller er IDLE")
                        Player.STATE_BUFFERING -> Log.d("PlayerManager", "Buffering...")
                        Player.STATE_READY -> Log.d("PlayerManager", "Klar til afspilning")
                        Player.STATE_ENDED -> {
                            Log.d("PlayerManager", "Afspilning færdig")
                            addPlayedId(appContext, PlaybackState.currentEntryId.toString())
                            Log.d("", PlaybackState.currentSearchResult?.kalturaId + " føjet til playedIds")
                        }
                    }
                }
            })

        }
        return player!!
    }

    fun setSearchResult(s: SearchResult) {
        searchResult = s
        PlaybackState.currentEntryId = s.kalturaId
        PlaybackState.currentSearchResult = s
    }


    fun addPlayedId(context: Context, id: String) {
        val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val playedIds = prefs.getStringSet("played_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        playedIds.add(id)
        prefs.edit { putStringSet("played_ids", playedIds).apply() }
    }


    private fun initMediaSession(context: Context, player: ExoPlayer) {
        // Opret MediaSession
        mediaSession = MediaSessionCompat(context, "PlayerManager").apply {
            isActive = true
        }

        // Forbind MediaSession til ExoPlayer via MediaSessionConnector
        mediaSessionConnector = MediaSessionConnector(mediaSession!!).apply {
            setPlayer(player)

            setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
                override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
                    val searchResult = PlaybackState.currentSearchResult
                    return MediaDescriptionCompat.Builder()
                        .setTitle(searchResult?.title ?: "Ukendt titel")
                        .setDescription(searchResult?.startTime ?: "Ukendt")
                        .build()
                }
            })
        }

    }

    private fun initNotification(context: Context) {
        createNotificationChannel(context)

        if (player == null || mediaSession == null) return

        val mediaDescriptionAdapter = object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return PlaybackState.currentSearchResult?.title ?: "Afspilning"
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = Intent(context, PlayerActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                return PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return formatDate(PlaybackState.currentSearchResult?.startTime.toString())
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                val entryId = PlaybackState.currentEntryId
                val imageUrl = "https://vod-cache.kaltura.nordu.net/p/397/sp/39700/thumbnail/entry_id/${entryId}/version/100002/height/640/width/640"

                val safeContext = context.applicationContext

                Glide.with(safeContext)
                    .asBitmap()
                    .load(imageUrl)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            callback.onBitmap(resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {}
                    })

                return null
            }
        }

        notificationManager = PlayerNotificationManager.Builder(context, NOTIFICATION_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(mediaDescriptionAdapter)
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {})
            .build().apply {
                setMediaSessionToken(mediaSession!!.sessionToken)

                // Aktiver ekstra knapper
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUseRewindAction(true)
                setUseFastForwardAction(true)

                // Brug evt. disse knapper i compact view
                //setCompactActionIndices(1, 2, 3) // Fx: rewind, play/pause, fastforward
            }

        notificationManager?.apply {
            setUsePlayPauseActions(true)         // (standard: true)
            setUseStopAction(true)               // stop
            setUsePreviousAction(true)           // <<
            setUseNextAction(true)               // >>
            setUseRewindAction(true)             // <<|
            setUseFastForwardAction(true)        // |>>
            setUseNextActionInCompactView(true)

        }


        notificationManager?.setUsePlayPauseActions(true)
        notificationManager?.setUseStopAction(true)
        notificationManager?.setPlayer(player)

        val serviceIntent = Intent(context, PlayerNotificationService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }



    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("d. MMMM yyyy", Locale("da", "DK"))
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    fun buildNotification(context: Context): Notification {
        val controller = mediaSession?.controller ?: return NotificationCompat.Builder(context, CHANNEL_ID).build()
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)
            setContentIntent(controller.sessionActivity)
            setDeleteIntent(
                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setSmallIcon(R.drawable.kglburger)


            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0)
            )
        }.build()
    }


    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        Log.d("", "manager: $manager")
    }


    // Starter afspilning af det angivne media-url
    fun startPlayback(context: Context, mediaUrl: String) {
        if (mediaUrl != currentMediaUrl) {
            //stop() // Stopper eventuel tidligere afspilning
            val mediaItem = MediaItem.fromUri(mediaUrl) // Opretter MediaItem
            val player = getPlayer(context) // Henter ExoPlayer
            player.setMediaItem(mediaItem) // Sætter MediaItem
            player.prepare() // Forbereder spilleren
            player.playWhenReady = false // Starter afspilning
            currentMediaUrl = mediaUrl // Opdaterer den nuværende URL

            try {
                PlayerManager.initNotification(context)
                Log.d("", "PlayerManager.initNotification(context) burde være kørt")
            } catch (e: Exception) {}
        }
    }

    // Stopper afspilning
    fun stop() {
        player?.run {
            clearMediaItems()
        }

        currentMediaUrl = null
        PlaybackState.currentSearchResult = null

        // AFBIND notifikationen, men behold MediaSession aktiv
        notificationManager?.setPlayer(null)
        notificationManager = null
    }


    // Frigiver ressourcerne
    fun release() {
        player?.release()
        player = null
        currentMediaUrl = null
        mediaSessionConnector?.setPlayer(null)
        mediaSession?.release()
        mediaSessionConnector = null
        mediaSession = null
        PlaybackState.currentSearchResult = null
    }
}