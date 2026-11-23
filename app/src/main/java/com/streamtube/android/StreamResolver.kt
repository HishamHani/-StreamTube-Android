package com.streamtube.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResolvedStream(val hlsUrl: String, val userAgent: String, val source: String)

object StreamResolver {
    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val VISION_VERSION = "1.02"
    private const val IOS_VERSION = "20.10.4"
    private const val VISION_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
    private const val IOS_UA = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolve(videoId: String): ResolvedStream = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        val clients = listOf(
            Triple("visionOS", VISION_VERSION, VISION_UA),
            Triple("iOS", IOS_VERSION, IOS_UA)
        )
        for ((name, version, userAgent) in clients) {
            try {
                val hls = requestPlayer(videoId, name, version, userAgent)
                if (hls.isNotBlank()) return@withContext ResolvedStream(hls, userAgent, name)
                failures += "$name returned no HLS manifest"
            } catch (t: Throwable) {
                failures += "$name: ${t.message ?: "request failed"}"
            }
        }
        error("Could not obtain a playable YouTube stream. " + failures.joinToString(" | "))
    }

    private fun fetchVisitorData(): String? {
        return try {
            val context = JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", "2.20251120.01.00")
                .put("hl", "en").put("gl", "US"))
            val body = JSONObject().put("context", context)
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .post(body).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("responseContext")?.optString("visitorData")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Throwable) { null }
    }

    private fun requestPlayer(videoId: String, name: String, version: String, userAgent: String): String {
        val clientName = if (name == "visionOS") "VISIONOS" else "IOS"
        val clientId = if (name == "visionOS") "101" else "5"
        val visitorData = if (name == "visionOS") fetchVisitorData() else null
        val client = JSONObject()
            .put("clientName", clientName)
            .put("clientVersion", version)
            .put("deviceMake", "Apple")
            .put("deviceModel", if (name == "visionOS") "RealityDevice17,1" else "iPhone16,2")
            .put("osName", if (name == "visionOS") "visionOS" else "iPhone")
            .put("osVersion", if (name == "visionOS") "26.5.23O471" else "18.3.2.22D82")
            .put("userAgent", userAgent)
            .put("hl", "en")
            .put("gl", "US")
        if (!visitorData.isNullOrBlank()) client.put("visitorData", visitorData)
        val body = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", client))
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(PLAYER_URL)
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("X-YouTube-Client-Name", clientId)
            .header("X-YouTube-Client-Version", version)
            .apply { if (!visitorData.isNullOrBlank()) header("X-Goog-Visitor-Id", visitorData) }
            .post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(raw)
            val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (status.isNotBlank() && status != "OK") {
                val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                error(reason.ifBlank { "YouTube playback status: $status" })
            }
            return json.optJSONObject("streamingData")?.optString("hlsManifestUrl").orEmpty()
                .ifBlank { error("No HLS manifest in YouTube response") }
        }
    }
}
