package com.github.tvbox.osc.player

import android.content.Context
import android.media.MediaPlayer
import android.view.Surface
import android.view.SurfaceHolder
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

class IjkPlayer(context: Context) : KernelPlayer() {
    private val TAG = "IjkPlayer"
    private var mockMediaPlayer: MediaPlayer? = null
    private val stateMachine = PlaybackStateMachine()
    private var awaitingPrepared = false
    private var pendingSpeed: Float? = null
    var isHardwareDecode = KV.get(HawkConfig.PLAYER_HARDWARE_DECODE, true)
        private set

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        isHardwareDecode = KV.get(HawkConfig.PLAYER_HARDWARE_DECODE, true)
        
        // 使用标准 MediaPlayer 作为降级方案
        val player = MediaPlayer()
        setupListeners(player)
        this.mockMediaPlayer = player
    }

    private fun setupListeners(player: MediaPlayer) {
        player.setOnPreparedListener {
            awaitingPrepared = false
            stateMachine.onPrepared()
            mPlayerEventListener?.onPrepared()
        }
        
        player.setOnCompletionListener {
            stateMachine.onCompletion()
            mPlayerEventListener?.onCompletion()
        }
        
        player.setOnErrorListener { _, what, extra ->
            LOG.e("$TAG onError: what=$what, extra=$extra")
            stateMachine.onError()
            mPlayerEventListener?.onError(what, extra)
            true
        }
        
        player.setOnInfoListener { _, what, extra ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    stateMachine.onBufferingStart()
                    mPlayerEventListener?.onInfo(MediaPlayer.MEDIA_INFO_BUFFERING_START, extra)
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    stateMachine.onBufferingEnd(mp.isPlaying)
                    mPlayerEventListener?.onInfo(MediaPlayer.MEDIA_INFO_BUFFERING_END, extra)
                }
                else -> false
            }
            true
        }
    }

    private val mp get() = mockMediaPlayer ?: throw IllegalStateException("Player not initialized")

    override fun setDataSource(path: String?, headers: Map<String, String>?) {
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
        try {
            mp.prepareAsync()
        } catch (e: Exception) {
            LOG.e("$TAG prepareAsync error: ${e.message}")
            stateMachine.onError()
            mPlayerEventListener?.onError(-1, 0)
        }
    }

    override fun start() {
        try {
            mp.start()
            stateMachine.onPlay()
        } catch (e: Exception) {
            LOG.e("$TAG start error: ${e.message}")
        }
    }

    override fun pause() {
        try {
            mp.pause()
            stateMachine.onPause()
        } catch (e: Exception) {
            LOG.e("$TAG pause error: ${e.message}")
        }
    }

    override fun resume() {
        start()
    }

    override fun stop() {
        try {
            mp.stop()
            stateMachine.onStopped()
            awaitingPrepared = false
        } catch (e: Exception) {
            LOG.e("$TAG stop error: ${e.message}")
        }
    }

    override fun release() {
        try {
            mp.release()
        } catch (e: Exception) {
            LOG.e("$TAG release error: ${e.message}")
        }
        mockMediaPlayer = null
        awaitingPrepared = false
    }

    override fun seekTo(msec: Long) {
        try {
            mp.seekTo(msec.toInt())
            stateMachine.onSeeking(msec)
        } catch (e: Exception) {
            LOG.e("$TAG seekTo error: ${e.message}")
        }
    }

    override fun getCurrentPosition(): Long {
        return try {
            mp.currentPosition.toLong()
        } catch (e: Exception) {
            0L
        }
    }

    override fun getDuration(): Long {
        return try {
            mp.duration.toLong()
        } catch (e: Exception) {
            0L
        }
    }

    override fun isPlaying(): Boolean {
        return try {
            mp.isPlaying
        } catch (e: Exception) {
            false
        }
    }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
    }

    override fun setSurface(surface: Surface?) {
        // MediaPlayer 使用 setDisplay
        mockMediaPlayer?.setDisplay(SurfaceTextureWrapper(surface))
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        mockMediaPlayer?.setDisplay(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        mockMediaPlayer?.setDisplay(null)
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
        KV.put(HawkConfig.PLAYER_HARDWARE_DECODE, isHardwareDecode)
        LOG.i("$TAG toggle decode mode: ${if (isHardwareDecode) "HARDWARE" else "SOFTWARE"}")
    }
}

// 辅助类：包装 Surface 到 SurfaceHolder
private class SurfaceTextureWrapper(private val surface: Surface?) : SurfaceHolder {
    override fun addCallback(callback: SurfaceHolder.Callback?) {}
    override fun removeCallback(callback: SurfaceHolder.Callback?) {}
    override fun setFrameSize(width: Int, height: Int) {}
    override fun setFixedSize(width: Int, height: Int) {}
    override fun setAspectRatio(width: Int, height: Int) {}
    override fun setType(type: Int) {}
    override fun getSurfaceInsets() = android.graphics.Rect()
    override fun lockCanvas() = null
    override fun lockCanvas(domain: android.graphics.Rect) = null
    override fun unlockAndPostCanvas(canvas: android.graphics.Canvas) {}
    override fun isCreating() = false
    override fun getSurface() = surface ?: throw IllegalStateException("No surface")
    override fun getSurfaceFrame() = android.util.Size(0, 0)
    override fun getSurfaceInsets(dst: android.graphics.Rect?) {}
}
