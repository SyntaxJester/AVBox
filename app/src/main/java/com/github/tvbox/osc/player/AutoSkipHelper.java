package com.github.tvbox.osc.player;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.KV;

import org.json.JSONObject;

/**
 * 自动跳过片头片尾和广告辅助类
 *
 * 支持两种播放器：
 * 1. 原生 MediaPlayer（IjkPlayer 使用）
 * 2. 通用 PlayerProxy 接口（ExoPlayer 等 Media3 播放器使用）
 *
 * 跳过时间来源优先级：
 * 1. API 元数据中的精确时间（通过 parseSkipInfo 传入）
 * 2. HawkConfig 中缓存的用户上次设置的时间
 * 3. 内置默认值（片头 90s / 片尾 30s / 广告 20s）
 */
public class AutoSkipHelper {
    private static final String TAG = "AutoSkipHelper";

    /** 默认跳过时长（毫秒） */
    public static final long DEFAULT_INTRO_END = 90_000L;
    public static final long DEFAULT_OUTRO_END = 30_000L;
    public static final long DEFAULT_AD_END = 20_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 片头/片尾/广告时间配置（毫秒）
    private long introStart = 0;
    private long introEnd = DEFAULT_INTRO_END;
    private long outroStart = 0;
    private long outroEnd = DEFAULT_OUTRO_END;
    private long adStart = 0;
    private long adEnd = DEFAULT_AD_END;

    private boolean introSkipped = false;
    private boolean outroSkipped = false;
    private boolean adSkipped = false;

    private MediaPlayer mediaPlayer;
    private PlayerProxy proxy;
    private Runnable checkRunnable;

    /** 检查间隔（毫秒） */
    private static final long CHECK_INTERVAL = 700L;

    /** 跳过回调，用于 UI 提示 */
    public interface SkipListener {
        void onSkipped(String type, long from, long to);
    }

    private SkipListener skipListener;

    /** 通用播放器代理接口，让非 MediaPlayer 的播放器也能复用本类 */
    public interface PlayerProxy {
        long getCurrentPosition();
        long getDuration();
        void seekTo(long position);
        boolean isPlaying();
    }

    public AutoSkipHelper(Context context) {
        this.context = context;
    }

    public void setSkipListener(SkipListener listener) {
        this.skipListener = listener;
    }

    public void setMediaPlayer(MediaPlayer player) {
        this.mediaPlayer = player;
        this.proxy = null;
    }

    /** 为 ExoPlayer 等 Media3 播放器设置代理 */
    public void setPlayerProxy(PlayerProxy p) {
        this.proxy = p;
        this.mediaPlayer = null;
    }

    private long currentPosition() {
        if (proxy != null) return proxy.getCurrentPosition();
        if (mediaPlayer != null) return mediaPlayer.getCurrentPosition();
        return 0L;
    }

    private long totalDuration() {
        if (proxy != null) return proxy.getDuration();
        if (mediaPlayer != null) return mediaPlayer.getDuration();
        return 0L;
    }

    private void doSeek(long position) {
        if (proxy != null) {
            proxy.seekTo(position);
        } else if (mediaPlayer != null) {
            mediaPlayer.seekTo((int) position);
        }
    }

    private boolean playing() {
        if (proxy != null) return proxy.isPlaying();
        if (mediaPlayer != null) return mediaPlayer.isPlaying();
        return false;
    }

    /**
     * 解析视频数据中的跳过信息
     *
     * 支持格式：
     * 1. 标准 JSON 字段：{"skip_intro_start":0,"skip_intro_end":90000,...}
     * 2. 描述文本标签：[skip=00:00-01:30]
     * 3. 分集标签：[skip=90]（表示片头 90 秒）
     *
     * @param vodInfo 视频信息 JSON 或纯文本
     */
    public void parseSkipInfo(String vodInfo) {
        if (vodInfo == null || vodInfo.trim().isEmpty()) return;
        try {
            String text = vodInfo.trim();

            // 格式 1：标准 JSON
            if (text.startsWith("{")) {
                JSONObject obj = new JSONObject(text);
                introStart = obj.optLong("skip_intro_start", introStart);
                introEnd = obj.optLong("skip_intro_end", introEnd);
                outroStart = obj.optLong("skip_outro_start", outroStart);
                outroEnd = obj.optLong("skip_outro_end", outroEnd);
                adStart = obj.optLong("skip_ad_start", adStart);
                adEnd = obj.optLong("skip_ad_end", adEnd);

                // 兼容旧字段名
                if (obj.has("skip_intro")) {
                    introStart = obj.optLong("skip_intro_start", 0);
                    introEnd = obj.optLong("skip_intro_end", DEFAULT_INTRO_END);
                }
                if (obj.has("skip_outro")) {
                    outroStart = obj.optLong("skip_outro_start", 0);
                    outroEnd = obj.optLong("skip_outro_end", DEFAULT_OUTRO_END);
                }

                // 从 vod_actor / vod_blurb 中提取 [skip=...] 标签
                String actor = obj.optString("vod_actor", "");
                String blurb = obj.optString("vod_blurb", "");
                if (actor.contains("[skip=")) parseSkipTag(actor);
                else if (blurb.contains("[skip=")) parseSkipTag(blurb);
            } else if (text.contains("[skip=")) {
                // 格式 2/3：纯文本标签
                parseSkipTag(text);
            }

            Log.i(TAG, "Parsed skip info: intro=[" + introStart + "-" + introEnd
                    + "] outro=[" + outroStart + "-" + outroEnd
                    + "] ad=[" + adStart + "-" + adEnd + "]");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse skip info", e);
        }
    }

    /**
     * 解析 [skip=00:00-01:30] 或 [skip=90] 格式的标签
     */
    private void parseSkipTag(String text) {
        try {
            int start = text.indexOf("[skip=");
            if (start < 0) return;
            int end = text.indexOf("]", start);
            if (end <= start) return;

            String skipStr = text.substring(start + 6, end).trim();
            if (skipStr.contains("-")) {
                // [skip=00:00-01:30] 起止时间
                String[] parts = skipStr.split("-");
                if (parts.length == 2) {
                    introStart = parseTime(parts[0].trim());
                    introEnd = parseTime(parts[1].trim());
                }
            } else {
                // [skip=90] 片头时长
                introEnd = parseTime(skipStr);
            }
            Log.i(TAG, "Parsed skip tag: [" + introStart + "-" + introEnd + "]");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse skip tag", e);
        }
    }

    /**
     * 解析时间字符串 (MM:SS / HH:MM:SS / SS / 毫秒)
     */
    private long parseTime(String timeStr) {
        try {
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                if (parts.length == 3) {
                    // HH:MM:SS
                    return Long.parseLong(parts[0]) * 3600000L
                            + Long.parseLong(parts[1]) * 60000L
                            + Long.parseLong(parts[2]) * 1000L;
                } else if (parts.length == 2) {
                    // MM:SS
                    return Long.parseLong(parts[0]) * 60000L
                            + Long.parseLong(parts[1]) * 1000L;
                }
            }
            long value = Long.parseLong(timeStr);
            // 大于 1000 视为已是毫秒，否则视为秒
            return value > 1000 ? value : value * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 HawkConfig 读取用户自定义的跳过时长
     */
    public void loadFromConfig() {
        introEnd = KV.get("skip_intro_end", (int) introEnd);
        outroEnd = KV.get("skip_outro_end", (int) outroEnd);
        adEnd = KV.get("skip_ad_end", (int) adEnd);
    }

    /**
     * 开始检查跳过
     */
    public void startChecking() {
        if (mediaPlayer == null && proxy == null) return;

        introSkipped = false;
        outroSkipped = false;
        adSkipped = false;

        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!playing()) {
                    handler.postDelayed(this, CHECK_INTERVAL);
                    return;
                }

                long pos = currentPosition();
                long dur = totalDuration();

                // 检查片头跳过
                if (!introSkipped && introEnd > 0 && pos >= introStart && pos < introEnd) {
                    if (KV.get(HawkConfig.AUTO_SKIP_INTRO, false)) {
                        Log.i(TAG, "Auto skip intro at " + pos + " -> " + introEnd);
                        doSeek(introEnd);
                        introSkipped = true;
                        notifySkip("intro", pos, introEnd);
                    } else {
                        introSkipped = true; // 未开启则不重复检查
                    }
                }

                // 检查广告跳过
                if (!adSkipped && adEnd > 0 && pos >= adStart && pos < adEnd) {
                    if (KV.get(HawkConfig.AUTO_SKIP_AD, false)) {
                        Log.i(TAG, "Auto skip ad at " + pos + " -> " + adEnd);
                        doSeek(adEnd);
                        adSkipped = true;
                        notifySkip("ad", pos, adEnd);
                    } else {
                        adSkipped = true;
                    }
                }

                // 检查片尾跳过（相对总时长计算）
                if (!outroSkipped && dur > 0 && outroEnd > 0) {
                    long outroThreshold = dur - outroEnd;
                    if (pos >= outroThreshold) {
                        if (KV.get(HawkConfig.AUTO_SKIP_OUTRO, false)) {
                            Log.i(TAG, "Auto skip outro at " + pos + ", ending soon");
                            notifySkip("outro", pos, dur);
                        }
                        outroSkipped = true;
                    }
                }

                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };

        handler.post(checkRunnable);
    }

    private void notifySkip(String type, long from, long to) {
        if (skipListener != null) {
            try {
                skipListener.onSkipped(type, from, to);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 停止检查
     */
    public void stopChecking() {
        if (checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
            checkRunnable = null;
        }
    }

    /**
     * 重置跳过状态（切换剧集时调用）
     */
    public void reset() {
        introSkipped = false;
        outroSkipped = false;
        adSkipped = false;
    }

    /**
     * 释放资源
     */
    public void release() {
        stopChecking();
        mediaPlayer = null;
        proxy = null;
        skipListener = null;
    }
}
