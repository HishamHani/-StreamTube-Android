package com.streamtube.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceLogin(val deviceCode: String, val userCode: String, val url: String, val interval: Long, val expires: Long)

object YouTubeAuth {
    // Public OAuth client identifier used by the upstream desktop project's YouTube TV sign-in flow.
    // This is not a password. The undocumented Google device endpoint can change without notice.
    private const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context, "youtube_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isSignedIn(context: Context) = prefs(context).getString("refresh_token", null).orEmpty().isNotBlank()
    fun signOut(context: Context) = prefs(context).edit().clear().apply()

    suspend fun begin(): DeviceLogin = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", CLIENT_ID).add("scope", SCOPE)
            .add("device_id", UUID.randomUUID().toString().replace("-", ""))
            .add("device_model", "ytlr::").build()
        val request = Request.Builder().url("https://www.youtube.com/o/oauth2/device/code").post(form).build()
        http.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string() ?: error("No response from Google"))
            if (!response.isSuccessful || json.has("error"))
                error(json.optString("error_description", json.optString("error", "Could not start sign-in")))
            DeviceLogin(json.getString("device_code"), json.getString("user_code"),
                json.optString("verification_url", "https://www.google.com/device"),
                json.optLong("interval", 5), json.optLong("expires_in", 1800))
        }
    }

    suspend fun approve(context: Context, login: DeviceLogin): String = withContext(Dispatchers.IO) {
        var interval = login.interval.coerceAtLeast(5)
        val endAt = System.currentTimeMillis() + login.expires * 1000
        while (System.currentTimeMillis() < endAt) {
            delay(interval * 1000)
            val form = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", login.deviceCode)
                .add("grant_type", "http://oauth.net/grant_type/device/1.0").build()
            val request = Request.Builder().url("https://www.youtube.com/o/oauth2/token").post(form).build()
            http.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "{}")
                when (json.optString("error")) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "expired_token" -> error("The code expired. Please try again.")
                    "access_denied" -> error("Sign-in was cancelled.")
                    else -> {
                        val access = json.optString("access_token")
                        if (access.isNotBlank()) {
                            prefs(context).edit()
                                .putString("access_token", access)
                                .putString("refresh_token", json.optString("refresh_token"))
                                .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                                .apply()
                            return@withContext "YouTube account connected"
                        }
                        if (!response.isSuccessful) error(json.optString("error_description", "Sign-in failed"))
                    }
                }
            }
        }
        error("The sign-in code expired. Please try again.")
    }
}
