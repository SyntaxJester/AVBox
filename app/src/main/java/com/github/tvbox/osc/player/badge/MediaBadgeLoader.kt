package com.github.tvbox.osc.player.badge

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.collection.LruCache
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

/**
 * 媒体徽章加载器：负责下载、缓存徽章包 JSON，并根据媒体信息匹配徽章。
 *
 * 对外保持兼容的静态 API：
 *   badgeItems(context, title) -> List<MediaBadge>
 *
 * 内部使用 BadgeStore 管理徽章包，BadgeMatcher 进行规则匹配。
 */
object MediaBadgeLoader {

    private val badgeCache = LruCache<String, List<MediaBadge>>(100)

    private var store: BadgeStore? = null
    private var storeInitialized = false

    private fun ensureStore(context: Context): BadgeStore {
        if (store == null || !storeInitialized) {
            store = BadgeStore(context.applicationContext)
            storeInitialized = true
        }
        return store!!
    }

    /**
     * 获取给定媒体标题匹配到的徽章列表。
     * 结果会缓存，避免重复匹配。
     */
    fun badgeItems(context: Context, title: String): List<MediaBadge> {
        val cacheKey = title
        val cached = badgeCache.get(cacheKey)
        if (cached != null) return cached

        val s = ensureStore(context)
        val badges = s.matchBadges(title)
        val result = badges.map { rule ->
            MediaBadge(
                name = rule.name,
                imageURL = rule.imageURL,
                type = MediaBadge.TYPE_PACK,
            )
        }

        badgeCache.put(cacheKey, result)
        return result
    }

    /**
     * 根据规则列表构建徽章显示数据（供外部调用）。
     */
    fun buildBadges(rules: List<BadgeRule>): List<MediaBadge> {
        return rules.map { rule ->
            MediaBadge(
                name = rule.name,
                imageURL = rule.imageURL,
                type = MediaBadge.TYPE_PACK,
            )
        }
    }

    /**
     * 清理资源（Activity 销毁时调用）。
     */
    fun destroy() {
        store?.destroy()
        store = null
        storeInitialized = false
        badgeCache.evictAll()
    }
}
