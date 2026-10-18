package com.github.tvbox.osc.player

import android.view.Surface
import android.view.SurfaceHolder
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.util.LOG
import kotlinx.coroutines.flow.StateFlow

abstract class KernelPlayer {

    interface Listener {
        fun onError()
        fun onCompletion()
        fun onInfo(what: Int, extra: Int)
        fun onPrepared()
        fun onVideoSizeChanged(width: Int, height: Int)
        fun onKernelPlayStateChanged() {}
    }

    @JvmField
    protected var mPlayerEventListener: Listener? = null

    @Volatile
    private var mStartPosition: Long = 0

    @Volatile
    private var startPositionApplied: Boolean = false

    abstract fun initPlayer()

    abstract fun setDataSource(path: String, headers: Map<String, String>?)

    abstract fun start(): Boolean

    abstract fun pause(): Boolean

    abstract fun stop(): Boolean

    open fun abortStart() {}

    abstract fun prepareAsync()

    fun setStartPosition(position: Long) {
        mStartPosition = maxOf(0L, position)
        startPositionApplied = false
    }

    protected val startPosition: Long
        get() = mStartPosition

    protected fun markStartPositionApplied() {
        startPositionApplied = true
    }

    fun isStartPositionApplied(): Boolean = startPositionApplied

    abstract fun reset()

    open fun keepRenderViewOnReset(): Boolean = false

    open fun resetTrackSelection() {}

    open fun getTrackInfo(): TrackInfo = TrackInfo()

    open fun setTrack(track: TrackInfoBean?) {}

    abstract val isPlaying: Boolean

    abstract val playState: PlayState

    abstract val stateFlow: StateFlow<PlayState>

    abstract fun seekTo(time: Long)

    abstract fun release()

    abstract val currentPosition: Long

    abstract val duration: Long

    abstract val bufferedPercentage: Int

    abstract fun setSurface(surface: Surface?)

    abstract fun setDisplay(holder: SurfaceHolder?)

    open fun clearDisplay() {
        setSurface(null)
    }

    abstract fun detachVideoSurface()

    abstract fun setVolume(leftVolume: Float, rightVolume: Float)

    abstract fun setLooping(isLooping: Boolean)

    abstract fun setOptions()

    abstract fun setSpeed(speed: Float)

    abstract val speed: Float

    // 自动跳过片头片尾配置
    var autoSkipEnabled = false
    var introDurationMs = 0L
    var outroDurationMs = 0L
    
    /**
     * 检查并执行自动跳过
     * @param currentPosition 当前播放位置（毫秒）
     * @param duration 视频总时长（毫秒）
     */
    fun checkAutoSkip(currentPosition: Long, duration: Long) {
        if (!autoSkipEnabled || duration <= 0) return
        // 跳过片头
        if (currentPosition < introDurationMs && introDurationMs > 0) {
            seekTo(introDurationMs)
            LOG.i("Auto-skip intro: $currentPosition -> ${introDurationMs}")
        }
        // 接近片尾时提示
        if (outroDurationMs > 0 && duration - currentPosition < outroDurationMs) {
            LOG.i("Approaching outro: ${duration - currentPosition}ms remaining")
        }
    }

    abstract val tcpSpeed: Long

    fun setPlayerEventListener(listener: Listener?) {
        mPlayerEventListener = listener
    }

    companion object {

        const val MEDIA_INFO_RENDERING_START = 3

        const val MEDIA_INFO_BUFFERING_START = 701

        const val MEDIA_INFO_BUFFERING_END = 702

        const val MEDIA_INFO_VIDEO_ROTATION_CHANGED = 10001
    }
}
