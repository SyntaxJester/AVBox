package com.github.tvbox.osc.player

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

/**
 * IJK 播放内核 - 占位实现
 * 需要添加 IJKMedia 依赖后才能使用
 */
class IjkPlayer(context: Context) : KernelPlayer() {

    companion object {
        private const val TAG = "IjkPlayer"
    }

    private val appContext: Context = context.applicationContext
    private val stateMachine = PlaybackStateMachine()
    private var pendingSpeed: Float? = null
    private var awaitingPrepared = false
    private var isHardwareDecode: Boolean = true
    private var mockMediaPlayer: MediaPlayer? = null

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        isHardwareDecode = KV.get(HawkConfig.PLAY_HARDWARE_DECODE, true)
        
        // 使用标准 MediaPlayer 作为降级方案
        val player = MediaPlayer()
        setupListeners(player)
        this.mockMediaPlayer = player
    }

    private fun setupListeners(player: MediaPlayer) {
        player.setOnPreparedListener {
            awaitingPrepared = false
            stateMachine.onPrepared()
            stateMachine.onRenderingStart()
            mPlayerEventListener?.onPrepared()
        }
        
        player.setOnCompletionListener {
            stateMachine.onCompletion()
            mPlayerEventListener?.onCompletion()
        }
        
        player.setOnErrorListener { mp, what, extra ->
            LOG.e("$TAG error: what=$what, extra=$extra")
            stateMachine.onError()
            mPlayerEventListener?.onError()
            true
        }
        
        player.setOnInfoListener { mp, what, extra ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    stateMachine.onBufferingStart()
                    mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, extra)
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    stateMachine.onBufferingEnd(mp.isPlaying)
                    mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, extra)
                }
            }
            false
        }
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        stateMachine.onContentReplaced()
        val player = mockMediaPlayer ?: return
        
        headers?.let {
            if (it.isNotEmpty()) {
                val sb = StringBuilder()
                it.forEach { (k, v) -> sb.append("$k: $v\r\n") }
                player.setDataSource(path, null, sb.toString())
            } else {
                player.setDataSource(path)
            }
        } ?: run {
            player.setDataSource(path)
        }
    }

    override fun prepareAsync() {
        val player = mockMediaPlayer ?: return
        if (!stateMachine.currentState.isInPlaybackState) return
        player.prepareAsync()
        awaitingPrepared = true
        stateMachine.onPrepareRequested()
        
        if (startPosition > 0) {
            player.seekTo(startPosition.toInt())
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

    override fun abortStart() {
        stateMachine.onStartAborted()
    }

    override fun reset() {
        awaitingPrepared = false
        mockMediaPlayer?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        mockMediaPlayer?.release()
        mockMediaPlayer = null
        awaitingPrepared = false
        stateMachine.onReset()
    }

    override fun seekTo(time: Long) {
        val player = mockMediaPlayer ?: return
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        player.seekTo(time.toInt())
    }

    override val isPlaying: Boolean get() = mockMediaPlayer?.isPlaying ?: false
    override val currentPosition: Long get() = (mockMediaPlayer?.currentPosition ?: 0).toLong()
    override val duration: Long get() = (mockMediaPlayer?.duration ?: 0).toLong()
    override val bufferedPercentage: Int get() = 0
    override val tcpSpeed: Long get() = 0L
    override val speed: Float get() = pendingSpeed ?: 1f

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
    }

    override fun setSurface(surface: Surface?) {
        mockMediaPlayer?.setVideoSurface(SurfaceTextureWrapper(surface))
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        mockMediaPlayer?.setDisplay(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        mockMediaPlayer?.setVideoSurface(null)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mockMediaPlayer?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        mockMediaPlayer?.isLooping = isLooping
    }

    override fun setOptions() = Unit

    /**
     * 切换解码模式（占位）
     */
    fun toggleDecodeMode() {
        isHardwareDecode = !isHardwareDecode
        KV.put(HawkConfig.PLAY_HARDWARE_DECODE, isHardwareDecode)
        LOG.i("$TAG toggle decode mode: ${if (isHardwareDecode) "HARDWARE" else "SOFTWARE"}")
    }
}

// 辅助类：包装 Surface 到 SurfaceTexture
private class SurfaceTextureWrapper(private val surface: Surface?) : SurfaceHolder {
    override fun addCallback(callback: SurfaceHolder.Callback?) {}
    override fun removeCallback(callback: SurfaceHolder.Callback?) {}
    override fun setFrameSize(width: Int, height: Int) {}
    override fun setFixedSize(width: Int, height: Int) {}
    override fun setAspectRatio(width: Int, height: Int) {}
    override fun setType(type: Int) {}
    override fun getSurfaceInsets(): android.graphics.Rect = android.graphics.Rect()
    override fun lockCanvas(): android.graphics.Canvas? = null
    override fun lockCanvas(domain: android.graphics.Rect): android.graphics.Canvas? = null
    override fun unlockAndPostCanvas(canvas: android.graphics.Canvas) {}
    override fun isCreating() = false
    override fun getSurface() = surface
    override fun getSurfaceFrame(): android.util.Size = android.util.Size(0, 0)
    override fun getSurfaceInsets(dst: android.graphics.Rect?) {}
}
