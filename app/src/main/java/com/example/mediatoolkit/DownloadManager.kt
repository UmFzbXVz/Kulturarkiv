package com.example.mediatoolkit

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.edit
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import okhttp3.*
import okio.buffer

object DownloadManager {

    private val client = OkHttpClient() 

    fun startDownload(context: Context, searchResult: SearchResult) {
        val kalturaId = searchResult.kalturaId ?: return
        ApiService.getMediaUrlviaAPI(kalturaId, forCast = true) { mediaUrl ->
            mediaUrl?.let { url ->
                val fileExtension = url.toString().substringAfterLast('.', "mp4")
                val fileName = "${formatDate(searchResult.startTime.toString())} - ${sanitizeFileName(searchResult.title)}.$fileExtension"

                Thread {
                    try {
                        val file = downloadFile(context, url.toString(), fileName)
                        if (file != null) {
                            val localUri = Uri.fromFile(file).toString()
                            saveLocalPath(context, kalturaId, localUri)
                            Log.d("DownloadManager", "Download succeeded: $localUri")
                        } else {
                            Log.e("DownloadManager", "Download failed for $kalturaId")
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Download error: ${e.message}", e)
                    }
                }.start()
            } ?: run {
                Log.e("DownloadManager", "mediaUrl is null")
            }
        }
    }

    private fun downloadFile(context: Context, url: String, fileName: String): File? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
            .addHeader("Referer", "https://www.kb.dk/")
            .addHeader("Origin", "https://www.kb.dk")
            .build()

        val progressListener = object : ProgressListener {
            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                if (done) {
                    Log.d("DownloadManager", "Download complete")
                } else if (contentLength != -1L) {
                    val progress = (100 * bytesRead) / contentLength
                    Log.d("DownloadManager", "Download progress: $progress%")
                }
            }
        }

        val clientWithProgress = client.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body!!, progressListener))
                    .build()
            }
            .build()

        val response: Response = clientWithProgress.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to download file: ${response.code}")
        }

        val body = response.body ?: return null
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val file = File(downloadsDir, fileName)
        body.byteStream().use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file
    }

    private fun sanitizeFileName(name: String?): String {
        val invalidChars = Regex("[\\\\/:*?\"<>|]")
        var sanitized = invalidChars.replace(name.toString(), "_")
        if (sanitized.length > 200) {
            sanitized = sanitized.substring(0, 200)
        }
        return sanitized.trim()
    }

    private fun saveLocalPath(context: Context, kalturaId: String, localUri: String) {
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        prefs.edit { putString(kalturaId, localUri) }
    }

    fun getLocalUri(context: Context, kalturaId: String): Uri? {
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val path = prefs.getString(kalturaId, null) ?: return null
        val file = File(Uri.parse(path).path ?: return null)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun formatDate(dateTimeString: String): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(dateTimeString))
    }

    interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, done: Boolean)
    }

    class ProgressResponseBody(private val responseBody: ResponseBody, private val progressListener: ProgressListener) : ResponseBody() {
        private var bufferedSource: BufferedSource? = null

        override fun contentType(): MediaType? = responseBody.contentType()

        override fun contentLength(): Long = responseBody.contentLength()

        override fun source(): BufferedSource {
            if (bufferedSource == null) {
                bufferedSource = source(responseBody.source()).buffer()
            }
            return bufferedSource!!
        }

        private fun source(source: Source): Source {
            return object : ForwardingSource(source) {
                var totalBytesRead = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1L)
                    return bytesRead
                }
            }
        }
    }
}
