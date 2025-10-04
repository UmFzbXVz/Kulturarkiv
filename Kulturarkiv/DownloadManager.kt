package com.example.mediatoolkit

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DownloadManager {

    fun startDownload(context: Context, searchResult: SearchResult, customFileName: String? = null) {
        if (!hasStoragePermission(context)) {
            Log.e("DownloadManager", "Storage permission not granted")
            return
        }

        val kalturaId = searchResult.kalturaId ?: return

        ApiService.getMediaUrlviaAPI(kalturaId, forCast = true) { mediaUrl ->
            mediaUrl?.let { url ->
                val fileExtension = url.toString().substringAfterLast('.', "mp4")

                val baseName = customFileName?.let {
                    sanitizeFileName(it.removeSuffix(".$fileExtension"))
                } ?: "${formatDate(searchResult.startTime.toString())} - ${sanitizeFileName(searchResult.title)}"

                val fileName = if (baseName.endsWith(".$fileExtension")) baseName else "$baseName.$fileExtension"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val downloadId = enqueueSystemDownload(context, url.toString(), fileName)
                        Log.d("DownloadManager", "System download enqueued: ID=$downloadId")

                        val filePath = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            fileName
                        ).absolutePath
                        saveLocalPath(context, kalturaId, Uri.fromFile(File(filePath)).toString())
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Download error: ${e.message}", e)
                    }
                }
            } ?: Log.e("DownloadManager", "mediaUrl is null")
        }
    }

    private fun enqueueSystemDownload(context: Context, url: String, fileName: String): Long {
        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription("Downloader…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("Referer", "https://www.kb.dk/")
            .addRequestHeader("Origin", "https://www.kb.dk/")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val systemDM = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return systemDM.enqueue(request)
    }

    private fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun sanitizeFileName(name: String?): String {
        val invalidChars = Regex("[\\\\/:*?\"<>|]")
        var sanitized = invalidChars.replace(name.toString(), "_")
        if (sanitized.length > 200) sanitized = sanitized.substring(0, 200)
        return sanitized.trim()
    }

    private fun saveLocalPath(context: Context, kalturaId: String, localUri: String) {
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        prefs.edit { putString(kalturaId, localUri) }
    }

    fun getLocalUri(context: Context, kalturaId: String): Uri? {
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val path = prefs.getString(kalturaId, null) ?: return null
        val file = File(path.toUri().path ?: return null)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun formatDate(dateTimeString: String): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(dateTimeString))
    }
}
