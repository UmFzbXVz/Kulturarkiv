package com.example.mediatoolkit

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DownloadManager {

    fun startDownload(context: Context, searchResult: SearchResult) {
        ApiService.getMediaUrlviaAPI(searchResult.kalturaId!!) { mediaUrl ->
            mediaUrl?.let { url ->
                Log.d("DownloadManager", "mediaUrl: $mediaUrl")
                Log.d("DownloadManager", "searchResult: $searchResult")

                // Ekstraher filtypen fra URL
                val fileExtension = getFileExtension(mediaUrl.toString())

                val request = DownloadManager.Request(mediaUrl).apply {
                    setTitle(formatDate(searchResult.startTime.toString()) + " - " + searchResult.title)
                    setDescription("Downloader mediefil..")
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "${formatDate(searchResult.startTime.toString())} - ${searchResult.title ?: "video"}.$fileExtension"  // Brug den korrekte filudvidelse
                    )
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(false)
                }

                val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                systemDownloadManager.enqueue(request)
                Log.d("DownloadManager", "Download enqueued.")
            } ?: run {
                Log.e("DownloadManager", "mediaUrl is null")
            }
        }
    }

    fun formatDate(dateTimeString: String): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(dateTimeString))
    }

    // Funktion til at hente filudvidelsen fra URL'en
    private fun getFileExtension(mediaUrl: String): String {
        val url = URL(mediaUrl)
        val path = url.path
        return path.substringAfterLast('.', ".mp4")  // Hvis der ikke er en udvidelse, bruges ".mp4" som standard
    }
}
