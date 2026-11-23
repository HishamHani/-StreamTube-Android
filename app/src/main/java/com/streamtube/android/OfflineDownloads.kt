package com.streamtube.android

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object OfflineDownloads {
    fun enqueue(context: Context, video: Video, format: DownloadFormat): Long {
        val safeTitle = video.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "")
            .trim()
            .take(90)
            .ifBlank { video.id }
        val fileName = "${safeTitle}_${video.id.take(8)}.${format.extension}"
        val request = DownloadManager.Request(Uri.parse(format.url))
            .setTitle(video.title)
            .setDescription("Stream Tube · ${format.label}")
            .setMimeType(format.mimeType)
            .addRequestHeader("User-Agent", format.userAgent.ifBlank { "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip" })
            .addRequestHeader("Origin", "https://www.youtube.com")
            .addRequestHeader("Referer", "https://www.youtube.com/")
            .apply { if (format.visitorData.isNotBlank()) addRequestHeader("X-Goog-Visitor-Id", format.visitorData) }
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "StreamTube/$fileName")
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
