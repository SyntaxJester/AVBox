package com.github.tvbox.osc.player.badge

import android.content.Context
import android.util.Patterns
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 媒体徽章加载器（基于 media-badge-kit 架构移植）。
 *
 * 负责：
 *   1. 从内置 res/raw/media_badges.json 或用户选定的远程链接加载徽章包
 *   2. 解析 groups + filters，按 group 顺序排序
 *   3. 对媒体标题执行正则匹配，返回匹配的徽章列表
 *
 * 与旧 MediaBadgeLoader 保持 API 兼容：badgeItems(context, title) / badgeNames(context, title)
 *
 * 包 JSON 格式：
 * {
 *   "groups": [{"id":"resolution","name":"分辨率"}, ...],
 *   "filters": [
 *     {"id":"4k","name":"4K","groupId":"resolution","pattern":"2160[Pp]|\\b4K\\b","imageURL":"https://..."}
 *   ]
 * }
 */
object MediaBadgeLoader {

    // ===== 缓存：一次加载，重复使用 =====
    private val rulesCache = AtomicReference<Pair<String, List<BadgeRule>>?>(null)
    // Pair<cacheKey, rules>

    /** 清除缓存，下次加载时重新下载。 */
    fun invalidate() {
        rulesCache.set(null)
    }

    /**
     * 根据设置确定徽章包来源 URL。
     */
    private fun selectedLinkKey(context: Context): String {
        val link = KV.get<Int>(HawkConfig.MEDIA_BADGES_SELECTED_LINK, HawkConfig.MEDIA_BADGE_LINK_1)
        val custom = KV.get<String>(HawkConfig.MEDIA_BADGES_CUSTOM_LINK, "")
        return when (link) {
            HawkConfig.MEDIA_BADGE_LINK_1 ->
                "https://media-badges.app.workbuddy.host/xinyue4capyplayer.json"
            HawkConfig.MEDIA_BADGE_LINK_2 ->
                "https://streambridge-studio-markers.vercel.app/badges-noframe.json"
            HawkConfig.MEDIA_BADGE_LINK_3 ->
                "https://raw.githubusercontent.com/zxxlxj/rex-m/main/xijing-club/pppboom.json"
            HawkConfig.MEDIA_BADGE_LINK_CUSTOM ->
                if (custom.isNotBlank()) custom else ""
            else -> ""
        }
    }

    /**
     * 同步返回与 [title] 匹配的徽章名称列表（保持向后兼容）。
     */
    fun badgeNames(context: Context, title: String): List<String> {
        return badgeItems(context, title).map { it.name }
    }

    /**
     * 同步返回与 [title] 匹配的 [MediaBadge] 列表（name + imageURL）。
     * 首次调用或链接变更时会下载并缓存徽章包。
     */
    fun badgeItems(context: Context, title: String): List<MediaBadge> {
        if (title.isBlank()) return emptyList()

        val key = selectedLinkKey(context)
        val cached = rulesCache.get()
        val rules: List<BadgeRule> = if (cached != null && cached.first == key) {
            cached.second
        } else {
            try {
                val packText = if (key.isBlank()) {
                    readRaw(context.applicationContext, R.raw.media_badges)
                } else {
                    fetch(key)
                }
                val result = BadgePackParser.parse(packText)
                val parsedRules = result.rules
                rulesCache.set(Pair(key, parsedRules))
                parsedRules
            } catch (e: Throwable) {
                LOG.e("media-badge load-failed: " + e.message)
                // 选定链接失败时，尝试内置包回退
                if (key.isNotBlank()) {
                    try {
                        val packText = readRaw(context.applicationContext, R.raw.media_badges)
                        val result = BadgePackParser.parse(packText)
                        rulesCache.set(Pair(key, result.rules))
                        result.rules
                    } catch (_: Throwable) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }

        return match(rules, title)
    }

    /** 对规则列表执行正则匹配，返回 MediaBadge 列表。 */
    private fun match(rules: List<BadgeRule>, title: String): List<MediaBadge> {
        if (rules.isEmpty()) return emptyList()
        val seen = java.util.HashSet<String>()
        val out = java.util.ArrayList<MediaBadge>()
        for (rule in rules) {
            if (rule.pattern.containsMatchIn(title) && seen.add(rule.name)) {
                out.add(
                    MediaBadge(
                        name = rule.name,
                        imageURL = rule.imageURL,
                        type = MediaBadge.TYPE_PACK,
                    )
                )
            }
        }
        return out
    }

    /** 从 assets/raw 读取内置徽章包文本。 */
    private fun readRaw(context: Context, resId: Int): String {
        context.resources.openRawResource(resId).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                return reader.readText()
            }
        }
    }

    /** 通过 OkHttp 下载远程徽章包文本。 */
    private fun fetch(url: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IllegalStateException("empty body")
        }
    }
}
