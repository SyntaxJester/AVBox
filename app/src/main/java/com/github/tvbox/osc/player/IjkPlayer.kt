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
    private var pendingSpeed: Float = 1.0f
    var isHardwareDecode = KV.get(HawkConfig.PLAYER_HARDWARE_DECODE, true)
        private set

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        isHardwareDecode = KV.get(HawkConfig.PLAYER_HARDWARE_DECODE, true)
        
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
            mPlayerEventListener?.onError()
            true
        }
        
        player.setOnInfoListener { _, what, extra ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    stateMachine.onBufferingStart()
                    mPlayerEventListener?.onInfo(MediaPlayer.MEDIA_INFO_BUFFERING_START, extra)
                }
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    stateMachine.onBufferingEnd(true)
                    mPlayerEventListener?.onInfo(MediaPlayer.MEDIA_INFO_BUFFERING_END, extra)
                }
                else -> false
            }
            true
        }
    }

    private val mp get() = mockMediaPlayer ?: throw IllegalStateException("Player not initialized")

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        val player = mockMediaPlayer ?: return
        
        if (!path.isNullOrEmpty()) {
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
    }

    override fun prepareAsync(): Boolean {
        return try {
            mp.prepareAsync()
            true
        } catch (e: Exception) {
            LOG.e("$TAG prepareAsync error: ${e.message}")
            stateMachine.onError()
            mPlayerEventListener?.onError()
            false
        }
    }

    override fun start(): Boolean {
        return try {
            mp.start()
            true
        } catch (e: Exception) {
            LOG.e("$TAG start error: ${e.message}")
            false
        }
    }

    override fun pause(): Boolean {
        return try {
            mp.pause()
            true
        } catch (e: Exception) {
            LOG.e("$TAG pause error: ${e.message}")
            false
        }
    }

    override fun stop(): Boolean {
        return try {
            mp.stop()
            stateMachine.onStopped()
            awaitingPrepared = false
            true
        } catch (e: Exception) {
            LOG.e("$TAG stop error: ${e.message}")
            false
        }
    }

    override fun reset() {
        try {
            mp.reset()
        } catch (e: Exception) {
            LOG.e("$TAG reset error: ${e.message}")
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

    override fun seekTo(time: Long) {
        try {
            mp.seekTo(time.toInt())
            stateMachine.onSeeking(time)
        } catch (e: Exception) {
            LOG.e("$TAG seekTo error: ${e.message}")
        }
    }

    override val currentPosition: Long
        get() = try { mp.currentPosition.toLong() } catch (e: Exception) { 0L }

    override val duration: Long
        get() = try { mp.duration.toLong() } catch (e: Exception) { 0L }

    override val bufferedPercentage: Int
        get() = 0

    override val speed: Float
        get() = pendingSpeed

    override val tcpSpeed: Long
        get() = 0L

    override val isPlaying: Boolean
        get() = try { mp.isPlaying } catch (e: Exception) { false }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        // MediaPlayer doesn't have native setSpeed, but we can track it
        LOG.i("$TAG setSpeed: $speed")
    }

    override fun setSurface(surface: Surface?) {
        mockMediaPlayer?.setSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        mockMediaPlayer?.setDisplay(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        setSurface(null)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        mockMediaPlayer?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        mockMediaPlayer?.isLooping = isLooping
    }

    override fun setOptions() = Unit

    fun toggleDecodeMode() {
        isHardwareDecode = !isHardwareDecode
        KV.put(HawkConfig.PLAYER_HARDWARE_DECODE, isHardwareDecode)
        LOG.i("$TAG toggle decode mode: ${if (isHardwareDecode) "HARDWARE" else "SOFTWARE"}")
    }
}
