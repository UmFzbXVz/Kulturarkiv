package com.example.mediatoolkit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.Player
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.cast.framework.*
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import android.content.res.Configuration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout


class PlayerActivity : AppCompatActivity() {

    private var entryId: String? = null
    private var searchResult: SearchResult? = null
    private lateinit var player: ExoPlayer
    private lateinit var castContext: CastContext
    private var generatedMediaUrl: String? = null
    private lateinit var playerView: PlayerView
    private var parsedEntryId: String? = null
    private var parsedFlavorId: String? = null
    private var parsedFileExt: String? = null
    private var parsedTitle: String? = null
    private var parsedDescription: String? = null
    private var parsedDate: String? = null
    private val processedEntryIds = mutableSetOf<String>()
    private var castPlaylist: List<Pair<String, String>> = listOf()
    private val mediaItemMetadataMap = mutableMapOf<String, SearchResult>()
    private var lastPlayedUri: String? = null
    private lateinit var wakeLockManager: WakeLockManager





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)


        // Strøm
        wakeLockManager = WakeLockManager(this)


        // Initialiser playerView
        playerView = findViewById(R.id.playerView)
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        player = PlayerManager.getPlayer(this)
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                // Det nye mediaItem (den vi skifter *til*)
                val currentUri = mediaItem?.localConfiguration?.uri?.toString() ?: return

                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    // Bruger har manuelt klikket "næste" forlæns/baglæns
                    mediaItemMetadataMap[currentUri]?.let {
                        setSearchResult(it)
                        updateUIFromMetadata()
                    }
                }

                lastPlayedUri?.let { previousUri ->
                    mediaItemMetadataMap[previousUri]?.let { previousSearchResult ->
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            PlayerManager.addPlayedId(this@PlayerActivity, previousSearchResult.kalturaId!!)
                        }
                    }
                }

                lastPlayedUri = currentUri
                mediaItemMetadataMap[currentUri]?.let { setSearchResult(it) }

                val nextEntryId = Regex("entryId/([\\w_\\-]+)")
                    .find(currentUri)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: return

                val shouldFetch = nextEntryId.isNotEmpty() && processedEntryIds.add(nextEntryId)
                if (!shouldFetch) return

                ApiService.fetchKalturaData(nextEntryId) { response ->
                    runOnUiThread {
                        refreshMeta(response)
                    }
                }
            }
        })




        // Hent playlist-elementer
        val playlistItems = intent.getParcelableArrayListExtra<SearchResult>("playlistItems") ?: ArrayList()
        val searchResult = intent.getParcelableExtra<SearchResult>("searchResult")


        if (searchResult == null) {
            // Håndter playliste
            val mediaItems = MutableList<MediaItem?>(playlistItems.size) { null }

            val totalItems = playlistItems.size
            var itemsProcessed = 0

            playlistItems.forEachIndexed { index, item ->
                item.kalturaId?.let { kalturaId ->
                    ApiService.getMediaUrlviaAPI(kalturaId) { mediaUrl ->
                        if (!isDestroyed && !isFinishing) {
                            runOnUiThread {
                                mediaUrl?.let { url ->
                                    val mediaItem = MediaItem.fromUri(url)
                                    mediaItems[index] = mediaItem
                                    mediaItemMetadataMap[url.toString()] =
                                        item // map URL til metadata
                                }
                            }
                            itemsProcessed++
                            if (itemsProcessed == totalItems) {
                                val finalList = mediaItems.filterNotNull()
                                player.clearMediaItems()
                                player.setMediaItems(finalList)
                                player.shuffleModeEnabled = false
                                player.prepare()
                                player.playWhenReady = false

                                // Byg castPlaylist baseret på mediaItems og metadata
                                castPlaylist = mediaItems.mapIndexedNotNull { idx, mediaItem ->
                                    val url = mediaItem?.localConfiguration?.uri?.toString()
                                    val title = playlistItems.getOrNull(idx)?.title
                                    val startTime = playlistItems.getOrNull(idx)?.startTime
                                    //Log.d("", "startTime for $mediaItem: $startTime")
                                    if (url != null && title != null) Pair(url, title) else null
                                }
                            }
                        }
                    }
                } ?: run {
                    itemsProcessed++
                }
            }
        }
        else {
            // Fortsæt med eksisterende afspilningslogik til håndtering af searchResult
            //Log.d("PlayerActivity", "Playing single search result: ${searchResult.title}")

            // SET SEARCHRESULT TIL PLAYERMANAGER
            setSearchResult(searchResult)

            if (searchResult.kalturaId != PlaybackState.currentSearchResult?.kalturaId) {
            // Hent mediaUrl via API
            ApiService.getMediaUrlviaAPI(searchResult.kalturaId!!) { mediaUrl ->
                runOnUiThread {
                    mediaUrl?.let { url ->
                        //player.clearMediaItems()
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.playWhenReady = false

                    }
                }
            }
            }
        }


        castContext = CastContext.getSharedInstance(this)
        val mediaRouteButton = findViewById<MediaRouteButton>(R.id.media_route_button)
        val mediaRouteSelector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
        mediaRouteButton.routeSelector = mediaRouteSelector

        mediaRouteButton.setOnClickListener {
            val session = castContext.sessionManager.currentCastSession
            if (session != null && session.isConnected && generatedMediaUrl != null) {
                //Log.d("mediaRouteButton.setOnClickListener", "Session: " + session.sessionId + "\n mediaUrl: " + generatedMediaUrl!!)
                startPlayer(generatedMediaUrl!!)
                castMedia(generatedMediaUrl!!)
            }
        }

        entryId = searchResult?.kalturaId
        if (entryId != null) {
            ApiService.fetchKalturaData(entryId!!) { response ->
                runOnUiThread {
                    handleApiResponse(response)
                    // Log.d("", "genEntryFlavorExt-test: " + getEntryFlavorExt(response.toString()))
                    // -> genEntryFlavorExt-test: https://vod-cache.kaltura.nordu.net/p/397/sp/39700/serveFlavor/entryId/0_rhjmlvff/v/12/flavorId/0_hvophoob/name/a.mp4
                }
            }
        }
    }

    private fun parseMetadata(responseData: String?) {
        try {
            responseData?.let {
                val jsonArray = JSONArray(it)
                val contextObject = jsonArray.getJSONObject(2)

                val flavorAssetsArray = contextObject.optJSONArray("flavorAssets")
                val sourcesArray = contextObject.optJSONArray("sources")

                parsedFlavorId = sourcesArray?.optJSONObject(0)?.optString("flavorIds")
                parsedFileExt = flavorAssetsArray?.optJSONObject(0)?.optString("fileExt")

                val objectsArray = jsonArray.getJSONObject(1).optJSONArray("objects")
                if (objectsArray != null && objectsArray.length() > 0) {
                    val mediaObject = objectsArray.getJSONObject(0)
                    parsedEntryId = mediaObject.optString("id")
                    parsedTitle = mediaObject.optString("name", "")
                    parsedDescription = mediaObject.optString("description", "")
                    parsedDate = formatDate(searchResult?.startTime.toString())
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Fejl ved parsing: ${e.message}")
        }
    }
    private fun updateUIFromMetadata() {
        searchResult = PlaybackState.currentSearchResult


        findViewById<TextView>(R.id.metadataTitle)?.text = searchResult?.title ?: "N/A"
        findViewById<TextView>(R.id.metadataDescription)?.text = PlaybackState.currentSearchResult?.description
            ?: "N/A"
        findViewById<TextView>(R.id.metadataStartTime)?.text = formatDate(searchResult?.startTime.toString())
    }


    private fun getSearchResultFromIntent(): SearchResult? {
        val result = intent.getParcelableExtra<SearchResult>("searchResult")
        //Log.d("PlayerActivity", "Received SearchResult: $result")

        if (result != null) {
            setSearchResult(result)
        }
        else {
            Log.d("", "setSearchResult i PlayerActivity er NULL")
        }

        return result
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

    private fun startPlaybackIfPossible() {
        if (!parsedEntryId.isNullOrEmpty() && !parsedFlavorId.isNullOrEmpty() && !parsedFileExt.isNullOrEmpty()) {
            val mediaUrl = generateKalturaStreamLink(parsedEntryId!!, parsedFlavorId!!, parsedFileExt!!)
            generatedMediaUrl = mediaUrl
            PlaybackState.currentEntryId = parsedEntryId
            startPlayer(mediaUrl)
        }
    }

    private fun setSearchResult(searchResult: SearchResult) {
        PlayerManager.setSearchResult(searchResult)
    }


    private fun startPlayer(mediaUrl: String) {
        PlayerManager.startPlayback(this, mediaUrl)
    }


    private fun handleApiResponse(responseData: String?) {
        runOnUiThread {
            parseMetadata(responseData)
            updateUIFromMetadata()
            startPlaybackIfPossible()
        }
    }

    private fun refreshMeta(responseData: String?) {
        runOnUiThread {
            parseMetadata(responseData)
            updateUIFromMetadata()
        }
    }



    private fun generateKalturaStreamLink(entryId: String, flavorId: String, fileExt: String): String {
        return "https://vod-cache.kaltura.nordu.net/p/397/sp/39700/serveFlavor/entryId/$entryId/v/12/flavorId/$flavorId/name/a.$fileExt"
    }

    private fun castMedia(mediaUrl: String? = null) {
        val castSession = castContext.sessionManager.currentCastSession
        val remoteMediaClient = castSession?.takeIf { it.isConnected }?.remoteMediaClient

        if (remoteMediaClient == null) {
            Toast.makeText(this, "Ingen aktiv Cast-session eller RemoteMediaClient utilgængelig", Toast.LENGTH_SHORT).show()
            return
        }

        if (castPlaylist.isNotEmpty()) {
            val mediaQueueItems = castPlaylist.mapNotNull { (url, _) ->
                val item = mediaItemMetadataMap[url] ?: return@mapNotNull null

                val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(MediaMetadata.KEY_TITLE, item.title.toString())
                    putString(MediaMetadata.KEY_SUBTITLE, formatDate(item.startTime.toString()))
                }

                val contentType = getContentType(url)

                val mediaInfo = MediaInfo.Builder(url)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(contentType)
                    .setMetadata(metadata)
                    .build()

                MediaQueueItem.Builder(mediaInfo).build()
            }

            if (mediaQueueItems.isNotEmpty()) {
                val queueData = MediaQueueData.Builder()
                    .setItems(mediaQueueItems.toMutableList())
                    .setStartIndex(0)
                    .setRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_OFF)
                    .build()

                val loadRequestData = MediaLoadRequestData.Builder()
                    .setQueueData(queueData)
                    .setAutoplay(true)
                    .build()

                remoteMediaClient.load(loadRequestData)
            } else {
                Toast.makeText(this, "Kunne ikke oprette cast-kø", Toast.LENGTH_SHORT).show()
            }

        } else if (mediaUrl != null) {
            val item = getSearchResultFromIntent()

            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, item?.title.toString())
                putString(MediaMetadata.KEY_SUBTITLE, formatDate(item?.startTime.toString()))
            }

            val contentType = getContentType(mediaUrl)

            val mediaInfo = MediaInfo.Builder(mediaUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()

            val loadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()

            remoteMediaClient.load(loadRequestData)
        } else {
            Toast.makeText(this, "Ingen media URL at caste", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getContentType(url: String): String {
        return when (url.substringAfterLast('.', "mp4").lowercase()) {
            "mp4" -> "video/mp4"
            "m3u8" -> "application/x-mpegURL"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "wav" -> "audio/wave"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "m4a" -> "audio/mp4"
            else -> "video/mp4"
        }
    }



    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d("CastDebug", "Session started")

            // Hvis vi har en castPlaylist, er det en playliste der skal castes
            if (castPlaylist.isNotEmpty()) {
                castMedia()
            }
            // Hvis vi kun har en enkelt video
            else if (generatedMediaUrl != null) {
                castMedia(generatedMediaUrl!!)
            }
        }


        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d("CastDebug", "Session resumed")
            // Når sessionen er genoptaget, cast media hvis nødvendigt
            if (generatedMediaUrl != null) {
                castMedia(generatedMediaUrl!!)
            }
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        setPlayerViewFullscreen(isLandscape)

        if (isLandscape) {
            val duration = searchResult?.durationMs?.toLong() ?: (10 * 60 * 1000L)
            wakeLockManager.acquire(durationMs = duration)
        } else {
            wakeLockManager.release()
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d("CastDebug", "onResume() - adding session listener")
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        // Detektér orientering af skærm og juster størrelse
        val currentOrientation = resources.configuration.orientation
        setPlayerViewFullscreen(currentOrientation == Configuration.ORIENTATION_LANDSCAPE)

        updateUIFromMetadata()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Opdater intent'et for aktiviteten
        Log.d("PlayerActivity", "onNewIntent triggered")

        // Hent og håndter nye data fra intent
        val newSearchResult = intent?.getParcelableExtra<SearchResult>("searchResult")
        if (newSearchResult != null) {
            Log.d("PlayerActivity", "Received new SearchResult via onNewIntent: ${newSearchResult.title}")
            setSearchResult(newSearchResult)
            updateUIFromMetadata()

            // Hent ny mediaUrl og start afspilning
            ApiService.getMediaUrlviaAPI(newSearchResult.kalturaId!!) { mediaUrl ->
                runOnUiThread {
                    mediaUrl?.let { url ->
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.playWhenReady = false
                    }
                }
            }
        }
    }



    private fun setPlayerViewFullscreen(isFullscreen: Boolean) {
        val layoutParams = playerView.layoutParams

        if (isFullscreen) {
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            // Skjul system bars
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.player_view_default_height)
            // Vis system bars igen
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }

        playerView.layoutParams = layoutParams
    }



    override fun onStart() {
        super.onStart()
        PlayerManager.getPlayer(this) // Sørger for at spilleren og sessionen er klar
    }

    override fun onStop() {
        super.onStop()
        Log.d("", "onStop kaldt i PlayerActivity")
        //PlayerManager.release() // Frigiver både spiller og mediasession
    }

    override fun onDestroy() {
        super.onDestroy()
        //PlayerManager.release()
        wakeLockManager.release()

        // debug
        Log.d("", "onDestroy kaldt i PlayerActivity")
    }
}