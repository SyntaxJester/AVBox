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
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * IJK 播放内核 - 基于 webhtv 的最佳实践优化
 * 支持硬件/软件解码切换、自定义缓冲区策略
 */
class IjkPlayer(context: Context) : KernelPlayer() {

    companion object {
        private const val TAG = "IjkPlayer"
        
        // IJK 选项分类
        private const val CATEGORY_FORMAT = IjkMediaPlayer.OPT_CATEGORY_FORMAT
        private const val CATEGORY_CODEC = IjkMediaPlayer.OPT_CATEGORY_CODEC
        private const val CATEGORY_PLAYER = IjkMediaPlayer.OPT_CATEGORY_PLAYER
        
        // 默认解码模式：1=硬件解码，0=软件解码
        private const val DEFAULT_HARDWARE_DECODE = true
    }

    private val appContext: Context = context.applicationContext
    private var ijkMediaPlayer: IjkMediaPlayer? = null
    private val stateMachine = PlaybackStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSpeed: Float? = null
    private var awaitingPrepared = false
    private var isHardwareDecode: Boolean = DEFAULT_HARDWARE_DECODE

    override val playState: PlayState
        get() = stateMachine.currentState

    override val stateFlow: StateFlow<PlayState>
        get() = stateMachine.state

    override fun initPlayer() {
        isHardwareDecode = KV.get(HawkConfig.PLAY_HARDWARE_DECODE, DEFAULT_HARDWARE_DECODE)
        
        val player = IjkMediaPlayer()
        configureIjkOptions(player)
        setupListeners(player)
        
        this.ijkMediaPlayer = player
    }

    /**
     * 配置 IJK 播放选项 - 参考 webhtv 最佳实践
     */
    private fun configureIjkOptions(player: IjkMediaPlayer) {
        // 格式解析优化
        player.setOption(CATEGORY_FORMAT, "dns_cache_clear", 1L)
        player.setOption(CATEGORY_FORMAT, "fflags", "fastseek")
        player.setOption(CATEGORY_FORMAT, "analyzeduration", 5000000L)
        player.setOption(CATEGORY_FORMAT, "probesize", 1000000L)
        player.setOption(CATEGORY_FORMAT, "timeout", 10000000L) // 10秒超时
        
        // 网络优化
        player.setOption(CATEGORY_FORMAT, "http-detect-range-support", 0L)
        player.setOption(CATEGORY_FORMAT, "reconnect", 1L)
        player.setOption(CATEGORY_FORMAT, "reconnect_streamed", 1L)
        player.setOption(CATEGORY_FORMAT, "reconnect_delay_max", 7L)
        
        // 解码优化
        player.setOption(CATEGORY_CODEC, "skip_frame", 1L) // 跳过非必要帧
        player.setOption(CATEGORY_CODEC, "skip_loop_filter", 0L)
        player.setOption(CATEGORY_CODEC, "skip_idct", 0L)
        player.setOption(CATEGORY_CODEC, "skip_filter", 0L)
        
        // 播放器优化
        player.setOption(CATEGORY_PLAYER, "infbuf", 1L) // 无限缓冲
        player.setOption(CATEGORY_PLAYER, "packet-buffering", 0L) // 禁用包缓冲
        player.setOption(CATEGORY_PLAYER, "framedrop", 1L) // 启用帧丢弃
        
        // 硬件解码配置
        if (isHardwareDecode) {
            player.setOption(CATEGORY_PLAYER, "mediacodec", 1L)
            player.setOption(CATEGORY_PLAYER, "mediacodec-all-videos", 1L)
            player.setOption(CATEGORY_PLAYER, "mediacodec-hevc", 1L)
            player.setOption(CATEGORY_PLAYER, "mediacodec-vp9", 1L)
            player.setOption(CATEGORY_PLAYER, "async", 1L)
        } else {
            player.setOption(CATEGORY_PLAYER, "mediacodec", 0L)
            player.setOption(CATEGORY_PLAYER, "mediacodec-hevc", 0L)
        }
        
        // HLS/流媒体优化
        player.setOption(CATEGORY_PLAYER, "opensles", 0L)
        player.setOption(CATEGORY_PLAYER, "overlay-format", 842225234L) // SDL_FOURCC_YUVA
        player.setOption(CATEGORY_PLAYER, "framedrop", 1L)
        player.setOption(CATEGORY_PLAYER, "start-on-prepared", 1L)
        
        // DNS 解析优化
        player.setOption(CATEGORY_FORMAT, "http-header", "Referer: https://www.google.com")
        player.setOption(CATEGORY_FORMAT, "user-agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
    }

    /**
     * 设置播放器监听器
     */
    private fun setupListeners(player: IjkMediaPlayer) {
        player.setOnPreparedListener { mp ->
            awaitingPrepared = false
            stateMachine.onPrepared()
            stateMachine.onRenderingStart()
            mPlayerEventListener?.onPrepared()
            mPlayerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
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
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    stateMachine.onRenderingStart()
                }
                IjkMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED -> {
                    mPlayerEventListener?.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, extra)
                }
            }
            false
        }
        
        player.setOnVideoSizeChangedListener { mp, width, height, sarNum, sarDen ->
            mPlayerEventListener?.onVideoSizeChanged(width, height)
        }
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        stateMachine.onContentReplaced()
        val player = ijkMediaPlayer ?: return
        
        headers?.let {
            if (it.isNotEmpty()) {
                val sb = StringBuilder("User-Agent: Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36\r\n")
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
        val player = ijkMediaPlayer ?: return
        if (!stateMachine.currentState.isInPlaybackState) return
        player.prepareAsync()
        awaitingPrepared = true
        stateMachine.onPrepareRequested()
        markStartPositionApplied()
        
        if (startPosition > 0) {
            player.seekTo(startPosition)
        }
    }

    override fun start(): Boolean {
        val player = ijkMediaPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        player.start()
        stateMachine.onPlayRequested()
        return true
    }

    override fun pause(): Boolean {
        val player = ijkMediaPlayer ?: return false
        if (!stateMachine.currentState.isInPlaybackState) return false
        if (!player.isPlaying) return false
        player.pause()
        stateMachine.onPauseRequested()
        return true
    }

    override fun stop(): Boolean {
        val player = ijkMediaPlayer ?: return false
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
        ijkMediaPlayer?.reset()
        stateMachine.onReset()
    }

    override fun release() {
        ijkMediaPlayer?.release()
        ijkMediaPlayer = null
        awaitingPrepared = false
        stateMachine.onReset()
    }

    override fun seekTo(time: Long) {
        val player = ijkMediaPlayer ?: return
        if (stateMachine.currentState == PlayState.PAUSED) {
            stateMachine.onSeekWhilePaused()
        }
        player.seekTo(time)
    }

    override val isPlaying: Boolean get() = ijkMediaPlayer?.isPlaying ?: false

    override val currentPosition: Long get() = ijkMediaPlayer?.currentPosition ?: 0L

    override val duration: Long get() = ijkMediaPlayer?.duration ?: 0L

    override val bufferedPercentage: Int get() = ijkMediaPlayer?.bufferPercentage ?: 0

    override val tcpSpeed: Long get() {
        try {
            return Math.max(0, ijkMediaPlayer?.getTcpSpeed() ?: 0L)
        } catch (e: Exception) {
            return 0L
        }
    }

    override val speed: Float get() = pendingSpeed ?: 1f

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        try {
            ijkMediaPlayer?.setSpeed(speed)
        } catch (e: Exception) {
            LOG.w("$TAG setSpeed failed: ${e.message}")
        }
    }

    override fun setSurface(surface: Surface?) {
        ijkMediaPlayer?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        ijkMediaPlayer?.setDisplay(holder)
    }

    override fun clearDisplay() {
        setSurface(null)
    }

    override fun detachVideoSurface() {
        ijkMediaPlayer?.setVideoSurface(null)
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        ijkMediaPlayer?.setVolume(leftVolume, rightVolume)
    }

    override fun setLooping(isLooping: Boolean) {
        ijkMediaPlayer?.isLooping = isLooping
    }

    override fun setOptions() {
        // IJK 选项已在 initPlayer 中配置
    }
    
    /**
     * 切换解码模式
     */
    fun toggleDecodeMode() {
        isHardwareDecode = !isHardwareDecode
        KV.put(HawkConfig.PLAY_HARDWARE_DECODE, isHardwareDecode)
        LOG.i("$TAG toggle decode mode: ${if (isHardwareDecode) "HARDWARE" else "SOFTWARE"}")
    }
}
