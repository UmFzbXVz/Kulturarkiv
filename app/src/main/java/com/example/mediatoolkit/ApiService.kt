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
    private val mediaCache = mutableMapOf<String, Uri?>() // Cache til medie-URL'er
    private lateinit var prefs: android.content.SharedPreferences


    // Function to handle the response and extract required values
    fun getEntryFlavorExt(responseData: String): String {
        responseData.let {
            try {
                var entryId = ""
                var fileExt = ""
                val contextObject = JSONArray(it).getJSONObject(2)
                val flavorAssetsArray = contextObject.optJSONArray("flavorAssets")
                val flavorId = contextObject.optJSONArray("sources")?.optJSONObject(0)?.optString("flavorIds")
                Log.d("", "flavorId: $flavorId")
                val mediaObject =
                    JSONArray(it).getJSONObject(1).optJSONArray("objects")?.optJSONObject(0)

                mediaObject?.let { media ->
                    entryId = media.optString("id")  // Opdaterer entryId
                    Log.d("", "entryId: $entryId")
                    fileExt = flavorAssetsArray?.optJSONObject(0)?.optString("fileExt") ?: ""  // Opdaterer fileExt
                    Log.d("", "fileExt: $fileExt")
                }
                val mediaUrl = "https://api.kltr.nordu.net/p/397/sp/39700/playManifest/entryId/$entryId/protocol/https/format/applehttp/flavorIds/$flavorId/a.m3u8?uiConfId=23454143&playSessionId=374da440-2887-3801-ac87-95eb3107b1ca:a5c2e371-641c-5204-e833-c1df53ed2bbf&referrer=aHR0cHM6Ly93d3cua2IuZGsvZmluZC1tYXRlcmlhbGUvZHItYXJraXZldC9wb3N0L2RzLnR2Om9haTppbzpiNTIxNmJhYi02OTdkLTRlMDEtYWM4Yy00NjM4YmVjMWY4ZmY=&clientTag=html5:v3.17.46"
                Log.d("", "mediaUrl genereret i API-modulet ($mediaUrl)")
                return mediaUrl

            } catch (e: Exception) {
                Log.d("", "Ting gik galt her")
            }
        }
        return ""
    }

    fun getMediaUrlviaAPI(entryId: String, onResult: (Uri?) -> Unit) {
        // Tjek om URL allerede er i cachen
        mediaCache[entryId]?.let { cachedUrl ->
            Log.d("KalturaData", "Henter fra cache for entryId: $entryId, URL: $cachedUrl")

            // Returner cached URL og sørg for at callback'en kører på hovedtråden
            runOnMainThread { onResult(cachedUrl) }
            return
        }

        Log.d("KalturaData", "Henter fra API (ikke cache) for entryId: $entryId")
        fetchKalturaData(entryId) { responseData ->
            responseData?.let {
                val mediaUrl = getEntryFlavorExt(it)
                Log.d("KalturaData", "Hentet fra API: $mediaUrl") // Logger mediaUrl
                mediaCache[entryId] = mediaUrl.toUri() // Gem i cache

                // Gem cache til prefs som JSON
                val jsonObject = JSONObject()
                mediaCache.forEach { (key, uri) ->
                    jsonObject.put(key, uri.toString())
                }
                prefs.edit() { putString("media_cache", jsonObject.toString()) }

                // Returner resultat på hovedtråden
                runOnMainThread { onResult(mediaUrl.toUri()) }
            } ?: run {
                Log.d("KalturaData", "Ingen data modtaget for entryId: $entryId")

                // Returner null resultat på hovedtråden
                runOnMainThread { onResult(null) }
            }
        }
    }
    private fun runOnMainThread(action: () -> Unit) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            // Hvis vi allerede er på hovedtråden, kan vi bare køre koden med det samme
            action()
        } else {
            // Ellers skub koden til hovedtråden
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
                        //Log.d("Kaltura response", jsonResponse.toString())
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

    // Opdateret til at returnere cookie og køre callback når klar
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

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences("api_service_prefs", android.content.Context.MODE_PRIVATE)
        authCookie = prefs.getString("auth_cookie", null)

        // Indlæs mediaCache fra prefs
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
