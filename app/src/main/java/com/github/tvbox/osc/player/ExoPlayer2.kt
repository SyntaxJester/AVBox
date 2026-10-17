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
        
        newEngine.playbackStateListener = { state -> dispatchPlaybackState(state) }
        newEngine.retryAsHlsListener = { awaitingPrepared = true }
        newEngine.addErrorListener { _, _ ->
            stateMachine.onError()
            mPlayerEventListener?.onError()
        }
        LOG.i("echo-m7b-exoplayer2-engine-ready")
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        stateMachine.onContentReplaced()
        engine?.setDataSource(path, headers, KV.get(HawkConfig.PLAYER_IS_LIVE, false))
    }

    override fun prepareAsync() {
        val current = engine ?: return
        current.setStartPosition(startPosition)
        if (!current.prepare()) return
        awaitingPrepared = true
        stateMachine.onPrepareRequested()
        markStartPositionApplied()
    }

    override fun start(): Boolean {
        val current = engine ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        current.start()
        stateMachine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        val current = engine ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        if (!current.isPlaying) return false
        current.pause()
        stateMachine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        val current = engine ?: return false
        if (stateMachine.currentState == PlayState.PAUSED) return false
        current.stop()
        stateMachine.onStopRequested()
        return true
    }

    override fun abortStart() {
        stateMachine.onStartAborted()
    }

    override fun reset() {
        awaitingPrepared = false
        engine?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        engine?.release()
        engine = null
        awaitingPrepared = false
        stateMachine.onReset()
    }

    override fun seekTo(time: Long) {
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        engine?.seekTo(time)
    }

    override val isPlaying: Boolean get() = engine?.isPlaying ?: false
    override val currentPosition: Long get() = engine?.currentPosition ?: 0L
    override val duration: Long get() = engine?.duration ?: 0L
    override val bufferedPercentage: Int get() = engine?.bufferedPercentage ?: 0
    override val tcpSpeed: Long get() = engine?.tcpSpeed ?: 0L
    override val speed: Float get() = engine?.speed ?: pendingSpeed ?: 1f

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        engine?.setSpeed(speed)
    }

    override fun setSurface(surface: Surface?) {
        engine?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        engine?.setDisplay(holder)
    }

    override fun clearDisplay() {
        engine?.clearVideoOutput()
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

    private fun dispatchPlaybackState(state: Int) {
        if (awaitingPrepared) {
            if (state == 3) { // Player.STATE_READY
                awaitingPrepared = false
                stateMachine.onPrepared()
                stateMachine.onRenderingStart()
                mPlayerEventListener?.onPrepared()
                mPlayerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
            }
            return
        }
        when (state) {
            2 -> { // Player.STATE_BUFFERING
                stateMachine.onBufferingStart()
                mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, engine?.bufferedPercentage ?: 0)
            }
            3 -> { // Player.STATE_READY
                stateMachine.onBufferingEnd(engine?.kernelIsPlaying == true)
                mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, engine?.bufferedPercentage ?: 0)
            }
            4 -> { // Player.STATE_ENDED
                stateMachine.onCompletion()
                mPlayerEventListener?.onCompletion()
            }
        }
    }
}
