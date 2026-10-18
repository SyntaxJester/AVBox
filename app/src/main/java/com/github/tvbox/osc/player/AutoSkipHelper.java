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
 * 基于时间戳配置的简单实现
 */
public class AutoSkipHelper {
    private static final String TAG = "AutoSkipHelper";
    
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // 片头/片尾/广告时间配置（毫秒）
    private long introStart = 0;
    private long introEnd = 0;
    private long outroStart = 0;
    private long outroEnd = 0;
    private long adStart = 0;
    private long adEnd = 0;
    
    private boolean introSkipped = false;
    private boolean outroSkipped = false;
    private boolean adSkipped = false;
    
    private MediaPlayer mediaPlayer;
    private Runnable checkRunnable;
    
    public AutoSkipHelper(Context context) {
        this.context = context;
    }
    
    public void setMediaPlayer(MediaPlayer player) {
        this.mediaPlayer = player;
    }
    
    /**
     * 解析视频数据中的跳过信息
     * @param vodInfo 视频信息JSON
     */
    public void parseSkipInfo(String vodInfo) {
        try {
            JSONObject obj = new JSONObject(vodInfo);
            
            // 尝试从vod_actor或vod_blurb中提取跳过信息
            String actor = obj.optString("vod_actor", "");
            String blurb = obj.optString("vod_blurb", "");
            
            // 支持格式: [skip=00:00-01:30]片头 或 {"intro":[0,90]}
            if (actor.contains("[skip=") || blurb.contains("[skip=")) {
                parseSkipTag(actor.contains("[skip=") ? actor : blurb);
            }
            
            // 尝试从自定义字段解析
            if (obj.has("skip_intro")) {
                introStart = obj.optLong("skip_intro_start", 0);
                introEnd = obj.optLong("skip_intro_end", 90000);
            }
            if (obj.has("skip_outro")) {
                outroStart = obj.optLong("skip_outro_start", 0);
                outroEnd = obj.optLong("skip_outro_end", 0);
            }
            
            Log.i(TAG, "Parsed skip info: intro=[" + introStart + "-" + introEnd + "] outro=[" + outroStart + "-" + outroEnd + "]");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse skip info", e);
        }
    }
    
    /**
     * 解析 [skip=00:00-01:30] 格式的标签
     */
    private void parseSkipTag(String text) {
        try {
            int start = text.indexOf("[skip=");
            if (start >= 0) {
                int end = text.indexOf("]", start);
                if (end > start) {
                    String skipStr = text.substring(start + 7, end);
                    String[] parts = skipStr.split("-");
                    if (parts.length == 2) {
                        introStart = parseTime(parts[0].trim());
                        introEnd = parseTime(parts[1].trim());
                        Log.i(TAG, "Parsed skip: [" + introStart + "-" + introEnd + "]");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse skip tag", e);
        }
    }
    
    /**
     * 解析时间字符串 (MM:SS 或 SS 或毫秒)
     */
    private long parseTime(String timeStr) {
        try {
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                return Long.parseLong(parts[0]) * 60000 + Long.parseLong(parts[1]) * 1000;
            } else {
                long seconds = Long.parseLong(timeStr);
                return seconds * 1000;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 开始检查跳过
     */
    public void startChecking() {
        if (mediaPlayer == null) return;
        
        introSkipped = false;
        outroSkipped = false;
        adSkipped = false;
        
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
                
                long pos = mediaPlayer.getCurrentPosition();
                
                // 检查片头跳过
                if (!introSkipped && introEnd > 0 && pos >= introStart && pos < introEnd) {
                    if (KV.get(HawkConfig.AUTO_SKIP_INTRO, true)) {
                        Log.i(TAG, "Auto skip intro at position " + pos);
                        mediaPlayer.seekTo((int) introEnd);
                        introSkipped = true;
                    }
                }
                
                // 检查片尾跳过
                if (!outroSkipped && outroStart > 0 && outroEnd > 0 && pos >= outroStart) {
                    if (KV.get(HawkConfig.AUTO_SKIP_OUTRO, true)) {
                        Log.i(TAG, "Auto skip outro at position " + pos);
                        mediaPlayer.seekTo((int) outroEnd);
                        outroSkipped = true;
                    }
                }
                
                // 检查广告跳过
                if (!adSkipped && adEnd > 0 && pos >= adStart && pos < adEnd) {
                    if (KV.get(HawkConfig.AUTO_SKIP_AD, true)) {
                        Log.i(TAG, "Auto skip ad at position " + pos);
                        mediaPlayer.seekTo((int) adEnd);
                        adSkipped = true;
                    }
                }
                
                // 继续检查
                handler.postDelayed(this, 1000);
            }
        };
        
        handler.post(checkRunnable);
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
     * 重置跳过状态
     */
    public void reset() {
        introSkipped = false;
        outroSkipped = false;
        adSkipped = false;
    }
}
