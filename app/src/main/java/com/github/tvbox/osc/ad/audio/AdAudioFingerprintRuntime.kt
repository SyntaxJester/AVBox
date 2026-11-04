package com.github.tvbox.osc.ad.audio

import android.os.Handler
import android.os.Looper
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG

/**
 * 音频指纹广告检测运行时。
 *
 * 复用 ad/audio 包内的 spectral-sequence-v2 算法实现：
 * - PCM 由 ExoPlayer 音频管线通过 [AdAudioTapProcessor] 无损采集；
 * - 采集到的 PCM 持续投喂 [AdAudioFingerprintHelper] 做流式匹配；
 * - 命中广告后回调播放器跳转到广告结束位置。
 *
 * 只有 ExoPlayer 内核支持 PCM 采集，IJK / MPV 等内核不参与指纹检测。
 */
object AdAudioFingerprintRuntime {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var helper: AdAudioFingerprintHelper? = null

    /** 当前生效的规则 JSON，null 表示没有规则 */
    private var rulesJson: String? = null

    @Volatile
    private var enabled: Boolean = KV.get(HawkConfig.AD_AUDIO_FINGERPRINT_ENABLED, false)

    /** 播放器跳转回调，由播放引擎注册 */
    @Volatile
    private var seekAction: ((Long) -> Unit)? = null

    /** 已经跳过的广告结束位置，避免重复触发 */
    private var lastSkippedEndMs: Long = 0L

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        enabled = value
        KV.put(HawkConfig.AD_AUDIO_FINGERPRINT_ENABLED, value)
        if (value) {
            startListening()
        } else {
            stopListening()
        }
        LOG.i("echo-ad-audio-fingerprint: enabled=$value")
    }

    /**
     * 设置规则 JSON。
     *
     * 格式（与 webhtv / ad/audio 包一致）：
     * ```json
     * {
     *   "schemaVersion": 2,
     *   "algorithm": "spectral-sequence-v2",
     *   "rules": [ { "id": "ad-1", "durationMs": 30000, "fingerprint": "..." } ]
     * }
     * ```
     */
    fun setRules(json: String?) {
        val newJson = json?.trim()
        if (newJson.isNullOrEmpty()) {
            rulesJson = null
            helper = null
            LOG.i("echo-ad-audio-fingerprint: rules cleared")
            return
        }
        if (newJson == rulesJson && helper != null) {
            return
        }
        rulesJson = newJson
        helper = null
        if (enabled) {
            startListening()
        }
    }

    fun hasRules(): Boolean = rulesJson != null

    /** 播放引擎注册跳转能力 */
    fun attachSeek(action: ((Long) -> Unit)?) {
        seekAction = action
    }

    /** 换片 / seek 之后重置匹配器，避免旧时间线的结果污染 */
    fun reset() {
        try {
            helper?.reset()
        } catch (e: Throwable) {
            LOG.e("echo-ad-audio-fingerprint reset failed: " + e.message)
        }
        lastSkippedEndMs = 0L
    }

    fun stopListening() {
        try {
            helper?.stopListening()
        } catch (e: Throwable) {
            LOG.e("echo-ad-audio-fingerprint stop failed: " + e.message)
        }
    }

    private fun startListening() {
        val json = rulesJson ?: return
        val current = helper
        if (current != null) {
            if (!current.isRunning) {
                current.startListening()
            }
            return
        }
        val created = AdAudioFingerprintHelper(AppContextHolderHolder.context())
        created.setMatchListener(object : AdAudioFingerprintHelper.MatchListener {
            override fun onMatchStart(ruleId: String, startTimeMs: Long, endTimeMs: Long) {
                // 只在完整命中后跳转，避免误报
            }

            override fun onMatchFull(ruleId: String, startTimeMs: Long, endTimeMs: Long) {
                onAdMatched(ruleId, startTimeMs, endTimeMs)
            }

            override fun onReset() {
            }
        })
        if (!created.loadRulesFromJson(json)) {
            LOG.i("echo-ad-audio-fingerprint: load rules failed")
            return
        }
        created.useConservativeConfig()
        created.startListening()
        helper = created
        LOG.i("echo-ad-audio-fingerprint: listening, rules=${created.ruleCount()}")
    }

    private fun onAdMatched(ruleId: String, startTimeMs: Long, endTimeMs: Long) {
        if (endTimeMs <= startTimeMs) return
        if (endTimeMs == lastSkippedEndMs) return
        lastSkippedEndMs = endTimeMs
        LOG.i("echo-ad-audio-fingerprint: match rule=$ruleId start=$startTimeMs end=$endTimeMs")
        mainHandler.post {
            val action = seekAction ?: return@post
            try {
                action(endTimeMs)
            } catch (e: Throwable) {
                LOG.e("echo-ad-audio-fingerprint: seek failed: " + e.message)
            }
        }
    }

    /** 由 [AdAudioTapProcessor] 调用，投喂 PCM */
    fun onPcm(samples: ShortArray, sampleRate: Int, channels: Int, startTimeMs: Long) {
        if (!enabled) return
        if (samples.isEmpty()) return
        val current = helper ?: return
        try {
            if (!current.isRunning) {
                current.startListening()
            }
            current.feed(samples, sampleRate, channels, startTimeMs)
        } catch (e: Throwable) {
            LOG.e("echo-ad-audio-fingerprint: feed failed: " + e.message)
        }
    }
}
