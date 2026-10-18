package com.github.tvbox.osc.player

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * MPV 播放器包装类
 * 注意: 需要 native libplayer.so 和 libmpv.so
 */
class MpvPlayer(context: Context) : KernelPlayer() {
    private val TAG = "MpvPlayer"
    
    override val playState: PlayState
        get() = TODO("Not implemented yet")
    
    override val stateFlow: StateFlow<PlayState>
        get() = TODO("Not implemented yet")
    
    override fun initPlayer() {
        TODO("Not implemented yet - requires native libraries")
    }
    
    override fun prepare(url: String) {
        TODO("Not implemented yet")
    }
    
    override fun seekTo(time: Long) {
        TODO("Not implemented yet")
    }
    
    override fun pause() {
        TODO("Not implemented yet")
    }
    
    override fun resume() {
        TODO("Not implemented yet")
    }
    
    override fun stop() {
        TODO("Not implemented yet")
    }
    
    override fun release() {
        TODO("Not implemented yet")
    }
    
    override fun setSurface(surface: android.view.Surface?) {
        TODO("Not implemented yet")
    }
    
    override fun setSpeed(speed: Float) {
        TODO("Not implemented yet")
    }
    
    override val speed: Float
        get() = TODO("Not implemented yet")
}
