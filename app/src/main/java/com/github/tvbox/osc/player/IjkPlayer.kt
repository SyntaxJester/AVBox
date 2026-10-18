package com.github.tvbox.osc.player

import android.content.Context
import android.media.MediaPlayer
import android.view.Surface
import android.view.SurfaceHolder
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

class IjkPlayer(context: Context) : KernelPlayer() {
    private val TAG = "IjkPlayer"
    private var mockMediaPlayer: MediaPlayer? = null
    val stateMachine = PlaybackStateMachine()
    private var pendingSpeed: Float? = null
    
    // 自动跳过片头片尾配置（毫秒）
    private var introDuration = 0L
    private var outroDuration = 0L
    private var adDuration = 0L
    
    // 是否开启自动跳过
    private var autoSkipIntro = false
    private var autoSkipOutro = false
    private var autoSkipAd = false

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        val player = MediaPlayer()
        setupListeners(player)
        this.mockMediaPlayer = player
    }

    private fun setupListeners(player: MediaPlayer) {
        player.setOnPreparedListener {
            stateMachine.onPrepared()
            stateMachine.onRenderingStart()
            mPlayerEventListener?.onPrepared()
        }
        
        player.setOnCompletionListener {
            stateMachine.onCompletion()
            mPlayerEventListener?.onCompletion()
        }
        
        player.setOnErrorListener { _, what, extra ->
            LOG.e("$TAG onError: what=$what, extra=$extra")
            stateMachine.onError()
            mPlayerEventListener?.onError()
            true
        }
        
        player.setOnInfoListener { _, what, extra ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    stateMachine.onBufferingStart()
                    mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, extra)
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    stateMachine.onBufferingEnd(true)
                    mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, extra)
                }
            }
            false
        }
        
        player.setOnVideoSizeChangedListener { _, width, height ->
            mPlayerEventListener?.onVideoSizeChanged(width, height)
        }
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        val player = mockMediaPlayer ?: return
        stateMachine.onContentReplaced()
        
        try {
            player.setDataSource(path)
        } catch (e: Exception) {
            LOG.e("$TAG setDataSource failed: ${e.message}")
        }
    }

    override fun prepareAsync() {
        val player = mockMediaPlayer ?: return
        try {
            player.prepareAsync()
            stateMachine.onPrepareRequested()
        } catch (e: Exception) {
            LOG.e("$TAG prepareAsync failed: ${e.message}")
        }
    }

    override fun start(): Boolean {
        val player = mockMediaPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        player.start()
        stateMachine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        val player = mockMediaPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        if (!player.isPlaying) return false
        player.pause()
        stateMachine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        val player = mockMediaPlayer ?: return false
        if (stateMachine.currentState == PlayState.PAUSED) return false
        player.stop()
        stateMachine.onStopRequested()
        return true
    }

    override fun reset() {
        val player = mockMediaPlayer
        player?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        val player = mockMediaPlayer ?: return
        player.release()
        mockMediaPlayer = null
        stateMachine.onReset()
    }

    override fun seekTo(time: Long) {
        val player = mockMediaPlayer ?: return
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        player.seekTo(time.toInt())
    }
    
    /**
     * 自动跳过片头片尾
     * @param duration 视频总时长（毫秒）
     */
    fun autoSkip(duraion: Long) {
        val currentTime = mockMediaPlayer?.currentPosition?.toLong() ?: 0L
        if (autoSkipIntro && currentTime < introDuration) {
            // 跳过片头
            mockMediaPlayer?.seekTo(introDuration.toInt())
            LOG.i("$TAG 自动跳过片头，从 ${currentTime}ms 跳到 ${introDuration}ms")
        }
        if (autoSkipOutro && currentTime > duration - outroDuration) {
            // 播放到最后几秒时提示或跳过
            LOG.i("$TAG 接近片尾，${duration - currentTime}ms 后结束")
        }
    }

    override val isPlaying: Boolean get() = mockMediaPlayer?.isPlaying ?: false
    override val currentPosition: Long get() = mockMediaPlayer?.currentPosition?.toLong() ?: 0L
    override val duration: Long get() = mockMediaPlayer?.duration?.toLong() ?: 0L
    override val bufferedPercentage: Int get() = 0
    override val speed: Float get() = pendingSpeed ?: 1.0f
    override val tcpSpeed: Long get() = 0L

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
    }

    override fun setSurface(surface: Surface?) {
        mockMediaPlayer?.setSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        mockMediaPlayer?.setDisplay(holder)
    }

    override fun clearDisplay() {
        mockMediaPlayer?.setDisplay(null)
    }

    override fun detachVideoSurface() {
        mockMediaPlayer?.setSurface(null)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mockMediaPlayer?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        mockMediaPlayer?.isLooping = isLooping
    }

    override fun setOptions() {
        // No options to set for MediaPlayer
    }
}
