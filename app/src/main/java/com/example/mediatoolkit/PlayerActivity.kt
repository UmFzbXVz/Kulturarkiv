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
import java.util.UUID


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
    private var playSessionId: String? = null





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

        if (searchResult != null) {
            if (searchResult.kalturaId == PlaybackState.currentEntryId) {
                Log.d("PlayerActivity", "Samme video, undgår genindlæsning og playback reset")
                updateUIFromMetadata()
            } else {
                Log.d("PlayerActivity", "Ny video, starter playback")
                setSearchResult(searchResult)
                entryId = searchResult.kalturaId
                entryId?.let {
                    ApiService.fetchKalturaData(it) { response ->
                        runOnUiThread {
                            handleApiResponse(response)
                        }
                    }
                }
            }
        }

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
                                    mediaItemMetadataMap[url.toString()] = item
                                }

                                itemsProcessed++

                                if (itemsProcessed == totalItems) {
                                    val finalList = mediaItems.filterNotNull()
                                    player.clearMediaItems()
                                    player.setMediaItems(finalList)
                                    player.shuffleModeEnabled = false
                                    player.prepare()
                                    player.playWhenReady = false

                                    castPlaylist = mediaItems.mapIndexedNotNull { idx, mediaItem ->
                                        val url = mediaItem?.localConfiguration?.uri?.toString()
                                        val title = playlistItems.getOrNull(idx)?.title
                                        if (url != null && title != null) Pair(url, title) else null
                                    }
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
            Log.d("PlayerActivity", "Playing single search result: ${searchResult.title}")
            setSearchResult(searchResult)
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
                    //handleApiResponse(response)
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

            // Tjek om mediet allerede er indlæst
            val currentMediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMediaUri == mediaUrl) {
                Log.d("PlayerActivity", "Mediet er allerede indlæst, springer afspilning over")
                return
            }
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

    private fun getOrCreatePlaySessionId(): String {
        if (playSessionId == null) {
            playSessionId = generatePlaySessionId()
        }
        return playSessionId!!
    }


    private fun generatePlaySessionId(): String {
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()
        return "$uuid1:$uuid2"
    }

    private fun generateKalturaStreamLink(entryId: String, flavorId: String, fileExt: String): String {
        val currentPlaySessionId = getOrCreatePlaySessionId()
        //Log.d("PlayerActivity", "Genererer kaltura-streamlink baseret på $entryId og $flavorId med playSessionId: $currentPlaySessionId")

        val baseUrl = "https://api.kltr.nordu.net/p/397/sp/39700/playManifest/entryId/$entryId/protocol/https"
        val queryParams = "?uiConfId=23454143&playSessionId=$currentPlaySessionId&referrer=aHR0cHM6Ly93d3cua2IuZGsvZmluZC1tYXRlcmlhbGUvZHItYXJraXZldC9wb3N0L2RzLnR2Om9haTppbzpiNTIxNmJhYi02OTdkLTRlMDEtYWM4Yy00NjM4YmVjMWY4ZmY=&clientTag=html5:v3.17.46"

        return when (fileExt) {
            "mp3" -> "$baseUrl/format/url/flavorIds/$flavorId/a.mp3$queryParams"
            else -> "$baseUrl/format/applehttp/flavorIds/$flavorId/a.m3u8$queryParams"
        }
    }


    private fun generateChromecastMediaUrl(entryId: String, flavorId: String, fileExt: String): String {
        return when (fileExt.lowercase()) {
            "mp3" -> "https://vod-cache.kaltura.nordu.net/p/397/sp/39700/serveFlavor/entryId/$entryId/v/12/flavorId/$flavorId/name/a.mp3"
            else -> "https://vod-cache.kaltura.nordu.net/hls/p/397/sp/39700/serveFlavor/entryId/$entryId/v/12/ev/1/flavorId/$flavorId/name/a.mp4/index.m3u8"
        }
    }

    private fun castMedia(mediaUrl: String? = null) {
        Log.d("PlayerActivity", "castMedia kaldet med mediaUrl: $mediaUrl")
        val castSession = castContext.sessionManager.currentCastSession
        val remoteMediaClient = castSession?.takeIf { it.isConnected }?.remoteMediaClient

        if (remoteMediaClient == null) {
            Toast.makeText(this, "Ingen aktiv Cast-session eller RemoteMediaClient utilgængelig", Toast.LENGTH_SHORT).show()
            return
        }

        if (castPlaylist.isNotEmpty()) {
            val mediaQueueItems = castPlaylist.mapNotNull { (url, _) ->
                val item = mediaItemMetadataMap[url] ?: return@mapNotNull null
                val chromecastUrl = generateChromecastMediaUrl(
                    item.kalturaId ?: return@mapNotNull null,
                    parsedFlavorId ?: return@mapNotNull null,
                    parsedFileExt ?: return@mapNotNull null
                )
                Log.d("PlayerActivity", "Chromecast URL for playlist item: $chromecastUrl")

                val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(MediaMetadata.KEY_TITLE, item.title.toString())
                    putString(MediaMetadata.KEY_SUBTITLE, formatDate(item.startTime.toString()))
                }

                val contentType = getContentType(chromecastUrl)

                val mediaInfo = MediaInfo.Builder(chromecastUrl)
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

        } else if (parsedEntryId != null && parsedFlavorId != null && parsedFileExt != null) {
            val item = getSearchResultFromIntent()
            val chromecastUrl = generateChromecastMediaUrl(parsedEntryId!!, parsedFlavorId!!, parsedFileExt!!)
            Log.d("PlayerActivity", "Chromecast URL for single item: $chromecastUrl")

            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, item?.title.toString())
                putString(MediaMetadata.KEY_SUBTITLE, formatDate(item?.startTime.toString()))
            }

            val contentType = getContentType(chromecastUrl)

            val mediaInfo = MediaInfo.Builder(chromecastUrl)
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
            Toast.makeText(this, "Ingen media URL eller metadata tilgængelig for casting", Toast.LENGTH_SHORT).show()
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
