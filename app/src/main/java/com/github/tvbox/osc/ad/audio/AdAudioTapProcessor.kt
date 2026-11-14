package com.github.tvbox.osc.ad.audio

import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 音频管线 PCM 采集器。
 *
 * ExoPlayer 将解码后的 PCM 依次传入 [queueInput]，我们在不改变数据的前提下
 * 复制一份投喂给 [AdAudioFingerprintRuntime] 做流式指纹比对。
 *
 * 注意：仅在 ExoPlayer 内核上注入；IJK/MPV 内核不适用。
 */
class AdAudioTapProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0

    /**
     * 直通缓冲：保存最近一次输入的完整副本，getOutput 时原样返还，
     * 保证音频数据不被丢弃、不重复。
     */
    private var passthrough: ByteBuffer = EMPTY_BUFFER

    override fun configure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // 输出格式与输入一致（纯直通）
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (sampleRate == 0 || channelCount == 0) {
            passthrough = inputBuffer
            return
        }
        // 记录输入窗口
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remainingBytes = limit - position
        if (remainingBytes <= 0) {
            passthrough = EMPTY_BUFFER
            return
        }

        // 复制一份样本供指纹使用
        val copy = ByteBuffer.allocate(remainingBytes).order(ByteOrder.nativeOrder())
        copy.put(inputBuffer)
        copy.flip()
        passthrough = copy

        // 回读样本投喂指纹运行时
        copy.position(0)
        val shortCount = remainingBytes / 2
        val samples = ShortArray(shortCount)
        copy.asShortBuffer()[samples]
        val posMs = android.os.SystemClock.elapsedRealtime()
        AdAudioFingerprintRuntime.onPcm(samples, sampleRate, channelCount, posMs)

        // 输入全部消费完
        inputBuffer.position(limit)
    }

    override fun isActive(): Boolean = true

    override fun queueEndOfStream() {
        // 纯直通，无需额外处理
    }

    override fun getOutput(): ByteBuffer {
        val out = passthrough
        passthrough = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = passthrough == EMPTY_BUFFER || !passthrough.hasRemaining()

    override fun flush() {
        passthrough = EMPTY_BUFFER
    }

    override fun reset() {
        flush()
        sampleRate =  fingerprintSampleRateFallback()
    }

    private fun fingerprintSampleRateFallback(): Int = 0

    private companion object {
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
