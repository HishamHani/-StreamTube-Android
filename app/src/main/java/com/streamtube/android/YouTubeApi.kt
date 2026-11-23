package com.streamtube.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String,
    val duration: String
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$id"
}

object YouTubeApi {
    private const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val CLIENT_VERSION = "2.20251120.01.00"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<Video> = withContext(Dispatchers.IO) {
        val context = JSONObject().put("client", JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
            .put("gl", "US"))
        val body = JSONObject().put("context", context).put("query", query)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(SEARCH_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Origin", "https://www.youtube.com")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("YouTube returned HTTP ${response.code}")
            val root = JSONObject(response.body?.string() ?: error("Empty response"))
            val found = mutableListOf<Video>()
            walk(root, found)
            found.distinctBy { it.id }.take(40)
        }
    }

    private fun walk(value: Any?, output: MutableList<Video>) {
        when (value) {
            is JSONObject -> {
                val renderer = value.optJSONObject("videoRenderer")
                if (renderer != null) parseVideo(renderer)?.let(output::add)
                val keys = value.keys()
                while (keys.hasNext()) walk(value.opt(keys.next()), output)
            }
            is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), output)
        }
    }

    private fun parseVideo(r: JSONObject): Video? {
        val id = r.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = text(r.optJSONObject("title")).ifBlank { "Untitled video" }
        val owner = r.optJSONObject("ownerText") ?: r.optJSONObject("longBylineText")
        val channel = text(owner)
        val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail = thumbs?.optJSONObject(thumbs.length().let { if (it > 0) it - 1 else 0 })
            ?.optString("url")?.takeIf { it.startsWith("http") }
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        val duration = text(r.optJSONObject("lengthText"))
        return Video(id, title, channel, thumbnail, duration)
    }

    private fun text(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
    }
}
