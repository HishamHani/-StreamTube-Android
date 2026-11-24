package com.streamtube.android

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
