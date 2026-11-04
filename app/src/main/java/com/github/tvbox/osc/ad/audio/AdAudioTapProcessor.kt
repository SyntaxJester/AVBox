package com.github.tvbox.osc.ad.audio

import androidx.media3.common.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Media3 音频管线 PCM 采集器。
 *
 * ExoPlayer 在将 PCM 交给声卡渲染前，会把样本依次传入 [process]。
 * 我们把 16-bit PCM 投喂给 [AdAudioFingerprintRuntime] 做流式指纹比对。
 *
 * 注意：这个类必须只在 ExoPlayer 内核上启用，IJK/MPV 内核不适用。
 */
class AdAudioTapProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0

    private val tempBuffer = ByteBuffer.allocate(4096).order(ByteOrder.nativeOrder())

    override fun configure(
        inputAudioFormat: AudioFormat,
        streamMetadata: androidx.media3.common.StreamMetadata?
    ): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // 告诉管线我们输出 PCM（保持不变，只是被动采样）
        return AudioFormat(
            sampleRate,
            inputAudioFormat.channelCount,
            inputAudioFormat.encoding,
            null, null, null
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.flip()
        val limit = inputBuffer.limit()
        if (sampleRate == 0 || channelCount == 0) return

        if (inputBuffer.hasArray()) {
            val array = inputBuffer.array
            val offset = inputBuffer.arrayOffset() + inputBuffer.position()
            val end = inputBuffer.limit()
            val samples = ShortArray(limit / 2)
            val sb = ShortBuffer.wrap(array, offset, limit / 2)
            sb.get(samples)
            val posMs = android.os.SystemClock.elapsedRealtime()
            AdAudioFingerprintRuntime.onPcm(samples, sampleRate, channelCount, posMs)
        } else {
            // Direct buffer：逐个 short 读取
            while (inputBuffer.hasRemaining()) {
                val chunk = min(2048, inputBuffer.remaining())
                val samples = ShortArray(chunk / 2)
                val sb = ShortBuffer.allocate(samples.size)
                // 把 direct 里的 short 先拷进 heap ShortBuffer
                for (i in samples.indices) {
                    if (!inputBuffer.hasRemaining()) break
                    samples[i] = inputBuffer.getShort()
                }
                val posMs = android.os.SystemClock.elapsedRealtime()
                AdAudioFingerprintRuntime.onPcm(samples, sampleRate, channelCount, posMs)
            }
        }
        inputBuffer.position(limit)
        tempBuffer.clear()
    }

    override fun isActive(): Boolean = true

    override fun queueEndOfStream() {
        // 无需特殊处理
    }

    override fun getOutput(): ByteBuffer {
        // 我们只是采样，不改变音频数据，返回空缓冲区表示已消费
        return AudioProcessor.EMPTY_BUFFER
    }

    override fun isEnded(): Boolean = false

    override fun flush() {
        tempBuffer.clear()
    }

    override fun reset() {
        flush()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
