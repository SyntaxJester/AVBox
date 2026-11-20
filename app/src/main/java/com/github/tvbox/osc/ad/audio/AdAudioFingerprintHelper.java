package com.github.tvbox.osc.ad.audio;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * 音频指纹广告检测辅助类
 *
 * 使用 spectral-sequence-v2 算法（纯 Java 实现，无需 native so）：
 * - 16kHz 采样率
 * - 512ms 窗口 / 256ms hop
 * - 16 频带 / 32-bit hash / 4 相位变体
 *
 * 典型用法：
 * <pre>
 * AdAudioFingerprintHelper helper = new AdAudioFingerprintHelper(context);
 * helper.loadRulesFromJson(json);
 * helper.setMatchListener(...);
 * helper.startListening();
 * // 播放过程中持续投喂 PCM：
 * helper.feed(pcmShorts, sampleRate, channels, positionMs);
 * </pre>
 */
public class AdAudioFingerprintHelper {
    private static final String TAG = "AdAudioFingerprint";

    private final Context context;
    private final AudioFingerprintConfig config;
    private AudioFingerprintRuleSet ruleSet;
    private AudioFingerprintMatcher matcher;
    private AudioFingerprintMatcher.Config matcherConfig;

    private volatile boolean running = false;

    /** 匹配回调 */
    public interface MatchListener {
        /** 命中广告起始（应立即跳过） */
        void onMatchStart(String ruleId, long startTimeMs, long endTimeMs);
        /** 完整序列命中（用于校验） */
        void onMatchFull(String ruleId, long startTimeMs, long endTimeMs);
        /** 匹配器重置（时间线跳变） */
        void onReset();
    }

    private MatchListener listener;

    public AdAudioFingerprintHelper(Context context) {
        this.context = context;
        this.config = AudioFingerprintConfig.standard();
        this.matcherConfig = AudioFingerprintMatcher.Config.conservative();
        this.ruleSet = AudioFingerprintRuleSet.empty();
        this.matcher = new AudioFingerprintMatcher(ruleSet, matcherConfig);
    }

    public void setMatchListener(MatchListener listener) {
        this.listener = listener;
    }

    /**
     * 从 JSON 加载规则集
     *
     * 格式：
     * {
     *   "schemaVersion": 2,
     *   "algorithm": "spectral-sequence-v2",
     *   "rules": [ { "id": "...", "durationMs": 30000, "fingerprint": "..." } ]
     * }
     */
    public boolean loadRulesFromJson(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                Log.w(TAG, "Empty rules JSON");
                return false;
            }
            AudioFingerprintRuleSet parsed = AudioFingerprintRuleCodec.fromJson(json);
            if (parsed == null || parsed.rules().isEmpty()) {
                Log.w(TAG, "No valid rules in JSON");
                return false;
            }
            this.ruleSet = parsed;
            this.matcher = new AudioFingerprintMatcher(ruleSet, matcherConfig);
            Log.i(TAG, "Loaded " + ruleSet.rules().size() + " rules");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load rules", e);
            return false;
        }
    }

    /** 使用更激进的快速启动配置（低延迟，误报率略高） */
    public void useFastStartConfig() {
        this.matcherConfig = AudioFingerprintMatcher.Config.fastStart();
        this.matcher = new AudioFingerprintMatcher(ruleSet, matcherConfig);
    }

    /** 使用保守配置（默认，低误报） */
    public void useConservativeConfig() {
        this.matcherConfig = AudioFingerprintMatcher.Config.conservative();
        this.matcher = new AudioFingerprintMatcher(ruleSet, matcherConfig);
    }

    /**
     * 开始监听
     */
    public void startListening() {
        if (ruleSet == null || ruleSet.rules().isEmpty()) {
            Log.w(TAG, "Cannot start: no rules loaded");
            return;
        }
        running = true;
        matcher.reset();
        Log.i(TAG, "Started listening");
    }

    /**
     * 停止监听
     */
    public void stopListening() {
        running = false;
        Log.i(TAG, "Stopped listening");
    }

    /**
     * 投喂 PCM 数据
     *
     * @param samples    16-bit PCM 交错样本
     * @param sampleRate 采样率（如 48000）
     * @param channels   声道数（1 或 2）
     * @param startTimeMs 该块起始播放位置（毫秒）
     */
    public void feed(short[] samples, int sampleRate, int channels, long startTimeMs) {
        if (!running || samples == null || samples.length == 0) return;
        try {
            AudioFingerprintMatcher.PcmChunk chunk =
                    new AudioFingerprintMatcher.PcmChunk(samples, sampleRate, channels, startTimeMs);
            AudioFingerprintMatcher.FeedResult result = matcher.feed(chunk);
            handleResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error feeding PCM", e);
        }
    }

    private void handleResult(AudioFingerprintMatcher.FeedResult result) {
        if (result == null) return;

        if (result.status() == AudioFingerprintMatcher.Status.RESET) {
            if (listener != null) listener.onReset();
            return;
        }

        List<AudioFingerprintMatcher.MatchEvent> events = result.events();
        if (events == null || events.isEmpty()) return;

        for (AudioFingerprintMatcher.MatchEvent event : events) {
            if (event == null) continue;
            if (event.type() == AudioFingerprintMatcher.Type.START_MATCHED) {
                Log.i(TAG, "MATCH START: " + event.ruleId() + " @" + event.startTimeMs());
                if (listener != null) {
                    listener.onMatchStart(event.ruleId(), event.startTimeMs(), event.endTimeMs());
                }
            } else if (event.type() == AudioFingerprintMatcher.Type.FULL_MATCHED) {
                Log.i(TAG, "MATCH FULL: " + event.ruleId() + " @" + event.startTimeMs());
                if (listener != null) {
                    listener.onMatchFull(event.ruleId(), event.startTimeMs(), event.endTimeMs());
                }
            }
        }
    }

    /**
     * 重置匹配器（切换剧集/seek 时调用）
     */
    public void reset() {
        if (matcher != null) matcher.reset();
        Log.i(TAG, "Reset");
    }

    /** 结算尾部样本 */
    public void finish() {
        if (matcher == null) return;
        try {
            AudioFingerprintMatcher.FeedResult result = matcher.finish();
            handleResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error finishing", e);
        }
    }

    public void release() {
        stopListening();
        matcher = null;
        listener = null;
        Log.i(TAG, "Released");
    }

    public boolean isEnabled() {
        return ruleSet != null && !ruleSet.rules().isEmpty();
    }

    public boolean isRunning() {
        return running;
    }

    public int ruleCount() {
        return ruleSet == null ? 0 : ruleSet.rules().size();
    }
}
