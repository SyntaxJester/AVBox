package com.github.tvbox.osc.player

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import com.github.tvbox.osc.player.engine.PlayerEngine
import com.github.tvbox.osc.player.engine.PlayerEngineConfig
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import kotlinx.coroutines.flow.StateFlow

class ExoPlayer2(context: Context) : KernelPlayer() {

    private val appContext: Context = context.applicationContext
    private var engine: PlayerEngine? = null
    private val stateMachine = PlaybackStateMachine()
    private var pendingSpeed: Float = 1.0f

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        val config = PlayerEngineConfig(
            bufferTimes = KV.get(HawkConfig.BUFFER_TIMES, HawkConfig.BUFFER_TIMES_DEFAULT).coerceIn(1, 10),
            tunnelingRequested = KV.get(HawkConfig.PLAY_TUNNEL, false),
            surfaceRender = KV.get(HawkConfig.PLAY_RENDER, 1) == 1,
            preferAac = KV.get(HawkConfig.PLAY_PREFER_AAC, false),
            dynamicScheduling = KV.get(
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING,
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING_DEFAULT,
            ),
        )
        engine = PlayerEngine(appContext, config)
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        engine?.setDataSource(path, headers)
    }

    override fun prepareAsync(): Boolean {
        return engine?.prepare() ?: false
    }

    override fun start(): Boolean {
        return try {
            engine?.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun pause(): Boolean {
        return try {
            engine?.pause()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun stop(): Boolean {
        return try {
            engine?.stop()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun reset() {
        engine?.reset()
    }

    override fun release() {
        engine?.release()
        engine = null
    }

    override fun seekTo(time: Long) {
        engine?.seekTo(time)
    }

    override val currentPosition: Long
        get() = engine?.currentPosition ?: 0L

    override val duration: Long
        get() = engine?.duration ?: 0L

    override val bufferedPercentage: Int
        get() = engine?.bufferedPercentage ?: 0

    override val isPlaying: Boolean
        get() = engine?.isPlaying ?: false

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        engine?.setSpeed(speed)
    }

    override val speed: Float
        get() = pendingSpeed

    override val tcpSpeed: Long
        get() = engine?.tcpSpeed ?: 0L

    override fun setSurface(surface: Surface?) {
        engine?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        engine?.setDisplay(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        engine?.detachVideoSurface()
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        engine?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        engine?.setLooping(isLooping)
    }

    override fun setOptions() {
        engine?.setOptions()
    }
}
