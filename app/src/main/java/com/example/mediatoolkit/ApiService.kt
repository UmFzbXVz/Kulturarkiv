package com.example.mediatoolkit

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import androidx.core.net.toUri
import androidx.core.content.edit

object ApiService {

    private val client = OkHttpClient()
    private var authCookie: String? = null
    private var hasRetriedAfter401 = false
    private val mediaCache = mutableMapOf<String, Uri?>()
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("api_service_prefs", android.content.Context.MODE_PRIVATE)
        authCookie = prefs.getString("auth_cookie", null)

        val cacheJson = prefs.getString("media_cache", null)
        cacheJson?.let {
            try {
                val jsonObject = JSONObject(it)
                for (key in jsonObject.keys()) {
                    val uriStr = jsonObject.optString(key, null)
                    mediaCache[key] = uriStr?.toUri()
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Kunne ikke indlæse mediaCache: ${e.message}")
            }
        }
    }

    fun parseUrlComponents(responseData: String?): Triple<String?, String?, String?>? {
        return try {
            responseData?.let {
                val jsonArray = JSONArray(it)
                val contextObject = jsonArray.getJSONObject(2)

                val flavorAssetsArray = contextObject.optJSONArray("flavorAssets")
                val sourcesArray = contextObject.optJSONArray("sources")

                val flavorId = sourcesArray?.optJSONObject(0)?.optString("flavorIds")
                val fileExt = flavorAssetsArray?.optJSONObject(0)?.optString("fileExt")

                val objectsArray = jsonArray.getJSONObject(1).optJSONArray("objects")
                if (objectsArray != null && objectsArray.length() > 0) {
                    val mediaObject = objectsArray.getJSONObject(0)
                    val entryId = mediaObject.optString("id")
                    return Triple(entryId, flavorId, fileExt)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("ApiService", "Fejl ved parsing: ${e.message}")
            null
        }
    }

    fun generateLocalStreamUrl(entryId: String, flavorId: String, fileExt: String, playSessionId: String): String {
        Log.d("ApiService", "Genererer local stream URL baseret på $entryId og $flavorId med playSessionId: $playSessionId")

        val baseUrl = "https://api.kltr.nordu.net/p/397/sp/39700/playManifest/entryId/$entryId/protocol/https"
        val queryParams = "?uiConfId=23454143&playSessionId=$playSessionId&referrer=aHR0cHM6Ly93d3cua2IuZGsvZmluZC1tYXRlcmlhbGUvZHItYXJraXZldC9wb3N0L2RzLnR2Om9haTppbzpiNTIxNmJhYi02OTdkLTRlMDEtYWM4Yy00NjM4YmVjMWY4ZmY=&clientTag=html5:v3.17.46"

        return when (fileExt) {
            "mp3" -> "$baseUrl/format/url/flavorIds/$flavorId/a.mp3$queryParams"
            else -> "$baseUrl/format/applehttp/flavorIds/$flavorId/a.m3u8$queryParams"
        }
    }

    fun generateCastUrl(entryId: String, flavorId: String, fileExt: String): String {
        return when (fileExt.lowercase()) {
            "mp3" -> "https://api.kltr.nordu.net/p/397/sp/39700/serveFlavor/entryId/$entryId/flavorId/$flavorId/name/a.mp3"
            else -> "https://api.kltr.nordu.net/p/397/sp/39700/serveFlavor/entryId/$entryId/flavorId/$flavorId/name/a.mp4"
        }
    }

    fun getMediaUrlviaAPI(entryId: String, forCast: Boolean = false, playSessionId: String? = null, onResult: (Uri?) -> Unit) {
        val cacheKey = "$entryId${if (forCast) "_cast" else "_local"}"

        mediaCache[cacheKey]?.let { cachedUrl ->
            Log.d("KalturaData", "Henter fra cache for $cacheKey: $cachedUrl")
            runOnMainThread { onResult(cachedUrl) }
            return
        }

        Log.d("KalturaData", "Henter fra API for $cacheKey")
        fetchKalturaData(entryId) { responseData ->
            responseData?.let {
                val components = parseUrlComponents(it)
                if (components != null) {
                    val (_, flavorId, fileExt) = components
                    if (flavorId != null && fileExt != null) {
                        val mediaUrl = if (forCast) {
                            generateCastUrl(entryId, flavorId, fileExt)
                        } else {
                            if (playSessionId == null) {
                                Log.e("ApiService", "playSessionId kræves for local URL")
                                runOnMainThread { onResult(null) }
                                return@let
                            }
                            generateLocalStreamUrl(entryId, flavorId, fileExt, playSessionId)
                        }
                        Log.d("KalturaData", "Genereret URL: $mediaUrl")
                        val uri = mediaUrl.toUri()
                        mediaCache[cacheKey] = uri // Cache

                        // Gem cache til prefs
                        val jsonObject = JSONObject()
                        mediaCache.forEach { (key, u) -> jsonObject.put(key, u.toString()) }
                        prefs.edit { putString("media_cache", jsonObject.toString()) }

                        runOnMainThread { onResult(uri) }
                    } else {
                        runOnMainThread { onResult(null) }
                    }
                } else {
                    runOnMainThread { onResult(null) }
                }
            } ?: runOnMainThread { onResult(null) }
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    fun fetchData(url: String, callback: (String?) -> Unit) {
        val requestBuilder = Request.Builder().url(url)
        authCookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API-kald", "Forespørgsel fejlede: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val contentEncoding = response.header("Content-Encoding")
                    val responseData = if (contentEncoding == "gzip") {
                        handleGzipResponse(response.body)
                    } else {
                        response.body?.string()
                    }

                    try {
                        val jsonResponse = JSONObject(responseData)
                        callback(responseData)
                    } catch (e: Exception) {
                        Log.e("Parse error", "Ugyldig JSON: $responseData")
                        callback(null)
                    }
                } else {
                    Log.e("API-kald", "Svarfejl med fejlkode: ${response.code}")

                    if (response.code == 401 && !hasRetriedAfter401) {
                        hasRetriedAfter401 = true
                        authenticateAndLogCookies {
                            fetchData(url, callback)
                        }
                    } else {
                        callback(null)
                    }
                }
                response.close()
            }
        })
    }

    fun fetchKalturaData(entryId: String, onResult: (String?) -> Unit) {
        Thread {
            try {
                val jsonPayload = String.format(
                    """{"1":{"service":"session","action":"startWidgetSession","widgetId":"_397"},"2":{"service":"baseEntry","action":"list","ks":"{1:result:ks}","filter":{"redirectFromEntryId":"%s"},"responseProfile":{"type":1,"fields":"id,referenceId,name,duration,description,thumbnailUrl,dataUrl,duration,msDuration,flavorParamsIds,mediaType,type,tags,startTime,date,dvrStatus,externalSourceType,status"}},"3":{"service":"baseEntry","action":"getPlaybackContext","entryId":"{2:result:objects:0:id}","ks":"{1:result:ks}","contextDataParams":{"objectType":"KalturaContextDataParams","flavorTags":"all"}},"4":{"service":"metadata_metadata","action":"list","filter":{"objectType":"KalturaMetadataFilter","objectIdEqual":"{2:result:objects:0:id}","metadataObjectTypeEqual":"1"},"ks":"{1:result:ks}"},"apiVersion":"3.3.0","format":1,"ks":"","clientTag":"html5:v3.14.4","partnerId":397}""",
                    entryId
                )

                val url = URL("https://api.kaltura.nordu.net/api_v3/service/multirequest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.setRequestProperty("Origin", "https://www.kb.dk")
                conn.setRequestProperty("Referer", "https://www.kb.dk/")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Connection", "keep-alive")
                conn.doOutput = true

                conn.outputStream.use { outputStream ->
                    outputStream.write(jsonPayload.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val responseText = responseStream.bufferedReader().use { it.readText() }

                onResult(responseText)

            } catch (e: Exception) {
                Log.e("Kaltura", "Fejl: ${e.message}")
                onResult(null)
            }
        }.start()
    }

    fun authenticateAndLogCookies(onComplete: () -> Unit) {
        val currentUnixTime = System.currentTimeMillis() / 1000

        val cookieHeader = """ppms_privacy_6c58358e-1595-4533-8cf8-9b1c061871d0={"visitorId":"0478c604-ce60-4537-8e17-fdb53fcd5c31","domain":{"normalized":"www.kb.dk","isWildcard":false,"pattern":"www.kb.dk"},"consents":{"analytics":{"status":1}}}; CookieScriptConsent={"bannershown":1,"action":"reject","consenttime":$currentUnixTime,"categories":"[]","key":"99a8bf43-ba89-444c-9333-2971c53e72a6"}"""

        val request = Request.Builder()
            .url("https://www.kb.dk/ds-api/bff/v1/authenticate/")
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Cookie", cookieHeader)
            .addHeader("Referer", "https://www.kb.dk/find-materiale/dr-arkivet/")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Auth", "Authentication failed: ${e.message}")
                onComplete() // Fortsæt alligevel, selv hvis auth fejler
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val cookies = response.headers("Set-Cookie")
                    val authHeader = cookies.find { it.contains("Authorization=") }
                    if (authHeader != null) {
                        val cleanCookie = authHeader.split(";")[0]
                        authCookie = cleanCookie
                        prefs.edit { putString("auth_cookie", cleanCookie) }
                        Log.d("Auth", "Cookie gemt: $authCookie")
                    } else {
                        Log.e("Auth", "No Authorization cookie found.")
                    }
                } else {
                    Log.e("Auth", "Authentication failed with code: ${response.code}")
                    response.close()
                }
                if (!response.isSuccessful) {
                    response.close()
                }

                onComplete()
            }
        })
    }


    private fun handleGzipResponse(responseBody: ResponseBody?): String? {
        if (responseBody == null) return null

        return try {
            val gzipStream = GZIPInputStream(responseBody.byteStream())
            val reader = InputStreamReader(gzipStream, Charsets.UTF_8)
            val stringBuilder = StringBuilder()

            reader.forEachLine {
                stringBuilder.append(it).append("\n")
            }

            stringBuilder.toString()
        } catch (e: Exception) {
            Log.e("ApiService", "Error decompressing response: ${e.message}")
            null
        }
    }
}
