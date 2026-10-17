package com.github.tvbox.osc.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.extractors
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

/**
 * ExoPlayer 播放内核 V2 - 参考 webhtv 的最佳实践
 * 支持自适应选轨、更好的错误处理
 */
class ExoPlayer2(context: Context) : KernelPlayer() {

    companion object {
        private const val TAG = "ExoPlayer2"
        private const val DEFAULT_BANDWIDTH_FLOOR = 500_000L // 500 kbps
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private val stateMachine = PlaybackStateMachine()
    private var pendingSpeed: Float? = null
    private var awaitingPrepare = false

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        val player = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(android.content.Context.POWER_SERVICE)
            .build()
        
        setupListeners(player)
        this.exoPlayer = player
    }

    private fun setupListeners(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_IDLE -> stateMachine.onReset()
                    Player.STATE_BUFFERING -> stateMachine.onBufferingStart()
                    Player.STATE_READY -> {
                        stateMachine.onPrepared()
                        if (player.playWhenReady) stateMachine.onRenderingStart()
                    }
                    Player.STATE_ENDED -> stateMachine.onCompletion()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) {
                    stateMachine.onPlayRequested()
                    stateMachine.onRenderingStart()
                    mPlayerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
                } else {
                    stateMachine.onPauseRequested()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                LOG.e("$TAG onPlayerError: ${error.errorCodeName}")
                stateMachine.onError()
                mPlayerEventListener?.onError()
            }

            override fun onTracksChanged(tracks: Tracks) {
                // 可选：处理轨道变化
            }
        })
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        stateMachine.onContentReplaced()
        val player = exoPlayer ?: return
        
        val mediaItem = buildMediaItem(path, headers)
        player.setMediaItem(mediaItem)
        player.prepare()
        awaitingPrepare = true
        stateMachine.onPrepareRequested()
        
        if (startPosition > 0) {
            player.seekTo(startPosition)
        }
    }

    private fun buildMediaItem(url: String, headers: Map<String, String>?): MediaItem {
        val uri = Uri.parse(url)
        val dataSourceFactory: DataSource.Factory = createDataSourceFactory(headers)
        
        return when {
            url.contains(".m3u8") || url.startsWith("rtsp://") -> {
                // HLS 或 RTSP 流
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(uri)
                    .setStreamType(MediaItem.STREAM_TYPE_LIVE)
                headers?.let { mediaItemBuilder.setHttpHeaders(buildHeaders(it)) }
                mediaItemBuilder.build()
            }
            url.contains(".mpd") -> {
                // DASH 流
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(uri)
                    .setStreamType(MediaItem.STREAM_TYPE_DASH)
                headers?.let { mediaItemBuilder.setHttpHeaders(buildHeaders(it)) }
                mediaItemBuilder.build()
            }
            else -> {
                // 普通文件
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(uri)
                    .setStreamType(MediaItem.STREAM_TYPE_DEFAULT)
                headers?.let { mediaItemBuilder.setHttpHeaders(buildHeaders(it)) }
                mediaItemBuilder.build()
            }
        }
    }

    private fun createDataSourceFactory(headers: Map<String, String>?): DataSource.Factory {
        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        
        headers?.let {
            defaultHttpDataSourceFactory.setDefaultRequestProperties(
                buildHeaders(it)
            )
        }
        
        return DefaultDataSource.Factory(appContext, defaultHttpDataSourceFactory)
    }

    private fun buildHeaders(headers: Map<String, String>): com.github.tvbox.osc.util.HttpUtil.RequestHeaders {
        return com.github.tvbox.osc.util.HttpUtil.RequestHeaders(headers)
    }

    override fun prepareAsync() {
        val player = exoPlayer ?: return
        if (!stateMachine.currentState.isInPlaybackState) return
        
        player.prepare()
        awaitingPrepare = true
        stateMachine.onPrepareRequested()
        markStartPositionApplied()
        
        if (startPosition > 0) {
            player.seekTo(startPosition)
        }
    }

    override fun start(): Boolean {
        val player = exoPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        
        player.play()
        stateMachine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        val player = exoPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        if (!player.isPlaying) return false
        
        player.pause()
        stateMachine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        val player = exoPlayer ?: return false
        if (stateMachine.currentState == PlayState.PAUSED) return false
        
        player.stop()
        stateMachine.onStopRequested()
        return true
    }

    override fun abortStart() {
        stateMachine.onStartAborted()
    }

    override fun reset() {
        awaitingPrepare = false
        exoPlayer?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        exoPlayer?.release()
        exoPlayer = null
        awaitingPrepare = false
        stateMachine.onReset()
    }

    override fun seekTo(time: Long) {
        val player = exoPlayer ?: return
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        player.seekTo(time)
    }

    override val isPlaying: Boolean get() = exoPlayer?.isPlaying ?: false

    override val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L

    override val duration: Long get() = exoPlayer?.duration ?: 0L

    override val bufferedPercentage: Int get() = exoPlayer?.bufferedPercentage ?: 0

    override val tcpSpeed: Long get() = 0L

    override val speed: Float get() = pendingSpeed ?: 1f

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    override fun setSurface(surface: Surface?) {
        exoPlayer?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        exoPlayer?.setVideoTextureView(null)
        exoPlayer?.setVideoSurfaceHolder(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        exoPlayer?.setVideoSurface(null)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        exoPlayer?.volume = rightVolume
    }

    override fun setLooping(isLooping: Boolean) {
        exoPlayer?.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun setOptions() {
        // ExoPlayer 选项已在构造时配置
    }
}
