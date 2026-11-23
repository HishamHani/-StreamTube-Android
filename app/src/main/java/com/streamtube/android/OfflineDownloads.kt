package com.streamtube.android

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

object OfflineDownloads {
    private val http = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    fun enqueue(context: Context, video: Video, format: DownloadFormat): Long {
        val safeTitle = video.title.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().take(90).ifBlank { video.id }
        val fileName = "${safeTitle}_${video.id.take(8)}.${format.extension}"

        // Adaptive YouTube video and audio are separate tracks. Download both and mux into a
        // single playable MP4 instead of saving a silent video-only file.
        if (!format.audioUrl.isNullOrBlank()) {
            Thread {
                var outputUri: Uri? = null
                val workDir = File(context.cacheDir, "offline_mux").apply { mkdirs() }
                val videoFile = File(workDir, "${video.id}_video.tmp")
                val audioFile = File(workDir, "${video.id}_audio.tmp")
                try {
                    downloadTrack(format.url, format.userAgent, format.visitorData, videoFile)
                    downloadTrack(format.audioUrl, format.audioUserAgent ?: format.userAgent,
                        format.audioVisitorData ?: format.visitorData, audioFile)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StreamTube")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    outputUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create the output file")
                    context.contentResolver.openFileDescriptor(outputUri, "w")?.use { descriptor ->
                        muxTracks(videoFile, audioFile, descriptor.fileDescriptor)
                    } ?: error("Could not open the output file")
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    context.contentResolver.update(outputUri, done, null, null)
                    Toast.makeText(context, "Saved to Downloads/StreamTube", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    outputUri?.let { context.contentResolver.delete(it, null, null) }
                    Toast.makeText(context, "Download failed: ${t.message ?: "could not merge tracks"}", Toast.LENGTH_LONG).show()
                } finally {
                    videoFile.delete()
                    audioFile.delete()
                }
            }.start()
            return System.currentTimeMillis()
        }

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

    private fun downloadTrack(url: String, userAgent: String, visitor: String, destination: File) {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .apply { if (visitor.isNotBlank()) header("X-Goog-Visitor-Id", visitor) }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("YouTube returned HTTP ${response.code}")
            val body = response.body ?: error("Empty download response")
            body.byteStream().use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
        }
    }

    private fun muxTracks(videoFile: File, audioFile: File, output: java.io.FileDescriptor) {
        val video = MediaExtractor()
        val audio = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            video.setDataSource(videoFile.absolutePath)
            audio.setDataSource(audioFile.absolutePath)
            val videoTrack = (0 until video.trackCount).firstOrNull {
                video.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("No video track found")
            val audioTrack = (0 until audio.trackCount).firstOrNull {
                audio.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No compatible audio track found")
            video.selectTrack(videoTrack)
            audio.selectTrack(audioTrack)
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideo = muxer.addTrack(video.getTrackFormat(videoTrack))
            val outAudio = muxer.addTrack(audio.getTrackFormat(audioTrack))
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = false
            while (!videoDone || !audioDone) {
                val useVideo = when {
                    videoDone -> false
                    audioDone -> true
                    else -> video.sampleTime <= audio.sampleTime
                }
                val extractor = if (useVideo) video else audio
                val outputTrack = if (useVideo) outVideo else outAudio
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    if (useVideo) videoDone = true else audioDone = true
                    continue
                }
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(outputTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            try { muxer?.release() } catch (_: Throwable) {}
            video.release()
            audio.release()
        }
    }
}
