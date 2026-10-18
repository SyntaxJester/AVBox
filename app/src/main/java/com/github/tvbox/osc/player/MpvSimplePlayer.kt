package com.github.tvbox.osc.player

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * 简化的 MPV 播放器包装
 * 注意：需要 native libplayer.so 和 libmpv.so
 */
class MpvSimplePlayer(private val context: Context) : KernelPlayer() {
    
    private var playerInstance: Long = 0L
    private var surface: Surface? = null
    
    override val playState: PlayState
        get() = TODO("需要实现 MPV JNI 绑定")
    
    override val stateFlow: StateFlow<PlayState>
        get() = TODO("需要实现 MPV JNI 绑定")
    
    init {
        // 加载 native 库
        try {
            System.loadLibrary("player")
            System.loadLibrary("mpv")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }
    
    override fun initPlayer() {
        // 初始化 MPV 实例
        // TODO: 调用 MPVLib.mpv_create()
    }
    
    override fun prepare(url: String) {
        // TODO: 调用 MPVLib.mpv_command_string("loadfile", url)
    }
    
    override fun seekTo(time: Long) {
        // TODO: 调用 MPVLib.mpv_seek(time / 1000.0)
    }
    
    override fun pause() {
        // TODO: 调用 MPVLib.mpv_command_string("pause")
    }
    
    override fun resume() {
        // TODO: 调用 MPVLib.mpv_command_string("pause")
    }
    
    override fun stop() {
        // TODO: 调用 MPVLib.mpv_command_string("stop")
    }
    
    override fun release() {
        if (playerInstance != 0L) {
            // TODO: 调用 MPVLib.mpv_destroy(playerInstance)
            playerInstance = 0L
        }
    }
    
    override fun setSurface(surface: Surface?) {
        this.surface = surface
        // TODO: 调用 MPVLib.mpv_set_option_string(instance, "wid", surfaceHandle)
    }
    
    override fun setSpeed(speed: Float) {
        // TODO: 调用 MPVLib.mpv_set_option_string(instance, "speed", speed.toString())
    }
    
    override val speed: Float
        get() = 1.0f
    
    // 检查是否支持 MPV
    companion object {
        private var supported: Boolean? = null
        
        fun isSupported(): Boolean {
            if (supported != null) return supported!!
            return try {
                System.loadLibrary("player")
                supported = true
            } catch (e: UnsatisfiedLinkError) {
                supported = false
                false
            }
        }
    }
}
