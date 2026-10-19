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
    
    /** 自动跳过辅助器 */
    private var autoSkipHelper: AutoSkipHelper? = null

    /** 外部注入的精确跳过配置（来自视频元数据） */
    private var pendingSkipConfig: String? = null

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        val player = MediaPlayer()
        setupListeners(player)
        this.mockMediaPlayer = player
        
        // 初始化自动跳过辅助器
        autoSkipHelper = AutoSkipHelper(context).apply {
            setMediaPlayer(player)
            setSkipListener { type, from, to ->
                LOG.i("$TAG AutoSkip: $type from $from to $to")
            }
        }
    }

    private fun setupListeners(player: MediaPlayer) {
        player.setOnPreparedListener {
            stateMachine.onPrepared()
            stateMachine.onRenderingStart()
            // 准备就绪后启动自动跳过检查
            startAutoSkip()
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
        autoSkipHelper?.stopChecking()
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
        autoSkipHelper?.release()
        autoSkipHelper = null
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
     * 启动自动跳过检查
     */
    private fun startAutoSkip() {
        val intro = com.github.tvbox.osc.util.KV.get(
            com.github.tvbox.osc.util.HawkConfig.AUTO_SKIP_INTRO, false
        )
        val outro = com.github.tvbox.osc.util.KV.get(
            com.github.tvbox.osc.util.HawkConfig.AUTO_SKIP_OUTRO, false
        )
        val ad = com.github.tvbox.osc.util.KV.get(
            com.github.tvbox.osc.util.HawkConfig.AUTO_SKIP_AD, false
        )
        if (!intro && !outro && !ad) return

        val helper = autoSkipHelper ?: return
        helper.reset()

        // 优先使用元数据中的精确配置，否则读取用户配置/默认值
        val cfg = pendingSkipConfig
        if (!cfg.isNullOrBlank()) {
            helper.parseSkipInfo(cfg)
        } else {
            helper.loadFromConfig()
        }
        helper.startChecking()
    }

    /**
     * 设置跳过配置（由上层从视频元数据注入）
     */
    fun setSkipConfig(json: String?) {
        pendingSkipConfig = json
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
