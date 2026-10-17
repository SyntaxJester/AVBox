package com.github.tvbox.osc.player

import android.content.Context
import com.github.tvbox.osc.player.engine.PlayerEngine
import com.github.tvbox.osc.player.engine.PlayerEngineConfig
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.player.state.PlaybackStateMachine
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

/**
 * ExoPlayer V2 - 基于 media3 的简化实现
 * 参考 webhtv 的 ExoPlayerEngine 设计
 */
class ExoPlayer2(context: Context) : KernelPlayer() {

    private val appContext: Context = context.applicationContext
    private var engine: PlayerEngine? = null
    private val stateMachine = PlaybackStateMachine()
    private var awaitingPrepared = false
    private var pendingSpeed: Float? = null

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        val config = PlayerEngineConfig(
            bufferTimes = bufferTimes(),
            tunnelingRequested = KV.get(HawkConfig.PLAY_TUNNEL, false),
            surfaceRender = KV.get(HawkConfig.PLAY_RENDER, 1) == 1,
            preferAac = KV.get(HawkConfig.PLAY_PREFER_AAC, false),
            dynamicScheduling = KV.get(
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING,
                HawkConfig.EXO_VIDEO_DYNAMIC_SCHEDULING_DEFAULT,
            ),
        )
        val newEngine = PlayerEngine(appContext, config)
        engine = newEngine
    }

    override fun setDataSource(path: String?, headers: Map<String, String>?) {
        val e = engine ?: return
        if (path.isNullOrEmpty()) return
        e.source = path
        headers?.let { e.headers = it }
    }

    override fun prepareAsync() {
        val e = engine ?: return
        awaitingPrepared = true
        e.prepare()
    }

    override fun start() {
        val e = engine ?: return
        e.start()
    }

    override fun pause() {
        val e = engine ?: return
        e.pause()
    }

    override fun resume() {
        val e = engine ?: return
        e.resume()
    }

    override fun stop() {
        val e = engine ?: return
        e.stop()
        awaitingPrepared = false
    }

    override fun release() {
        val e = engine ?: return
        e.release()
        engine = null
    }

    override fun seekTo msec: Long) {
        val e = engine ?: return
        e.seekTo(msec)
    }

    override fun getCurrentPosition(): Long {
        val e = engine ?: return 0L
        return e.getCurrentPosition()
    }

    override fun getDuration(): Long {
        val e = engine ?: return 0L
        return e.getDuration()
    }

    override fun isPlaying(): Boolean {
        val e = engine ?: return false
        return e.isPlaying()
    }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        engine?.setSpeed(speed)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        engine?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        engine?.setLooping(isLooping)
    }

    private fun bufferTimes(): Int {
        return 3000 // 默认 3 秒缓冲
    }
}
