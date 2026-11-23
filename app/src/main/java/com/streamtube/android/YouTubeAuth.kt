package com.streamtube.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceLogin(val deviceCode: String, val userCode: String, val url: String, val interval: Long, val expires: Long)

object YouTubeAuth {
    // These are the same public YouTube TV OAuth client credentials and device flow
    // used by the upstream desktop project. They are app credentials, not user credentials.
    private const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    private const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    private const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context, "youtube_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isSignedIn(context: Context) = prefs(context).getString("refresh_token", null).orEmpty().isNotBlank()
    fun signOut(context: Context) = prefs(context).edit().clear().apply()

    fun openVerification(context: Context, login: DeviceLogin) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(login.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun postOAuth(url: String, payload: JSONObject): JSONObject {
        val body = payload.toString().toRequestBody(jsonType)
        val request = Request.Builder().url(url)
            .header("User-Agent", TV_UA)
            .header("Accept", "application/json")
            .post(body).build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful && !json.has("error")) {
                error("Google sign-in returned HTTP ${response.code}")
            }
            return json
        }
    }

    suspend fun begin(): DeviceLogin = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("client_id", CLIENT_ID)
            .put("scope", SCOPE)
            .put("device_id", UUID.randomUUID().toString().replace("-", ""))
            .put("device_model", "ytlr::")
        val json = postOAuth("https://www.youtube.com/o/oauth2/device/code", payload)
        if (json.has("error")) error(json.optString("error_description", json.optString("error")))
        DeviceLogin(
            json.getString("device_code"),
            json.getString("user_code"),
            json.optString("verification_url", "https://www.google.com/device"),
            json.optLong("interval", 5),
            json.optLong("expires_in", 1800)
        )
    }

    suspend fun approve(context: Context, login: DeviceLogin): String = withContext(Dispatchers.IO) {
        var interval = login.interval.coerceAtLeast(5)
        val endAt = System.currentTimeMillis() + login.expires * 1000
        while (System.currentTimeMillis() < endAt) {
            delay(interval * 1000)
            val json = postOAuth(
                "https://www.youtube.com/o/oauth2/token",
                JSONObject()
                    .put("client_id", CLIENT_ID)
                    .put("client_secret", CLIENT_SECRET)
                    .put("code", login.deviceCode)
                    .put("grant_type", "http://oauth.net/grant_type/device/1.0")
            )
            when (json.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("The code expired. Please try again.")
                "access_denied" -> error("Sign-in was cancelled.")
                else -> {
                    val access = json.optString("access_token")
                    if (access.isNotBlank()) {
                        val refresh = json.optString("refresh_token")
                        if (refresh.isBlank()) error("Google did not return a refresh token. Please try signing in again.")
                        prefs(context).edit()
                            .putString("access_token", access)
                            .putString("refresh_token", refresh)
                            .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                            .apply()
                        return@withContext "YouTube account connected"
                    }
                    error(json.optString("error_description", "Sign-in failed"))
                }
            }
        }
        error("The sign-in code expired. Please try again.")
    }
}
