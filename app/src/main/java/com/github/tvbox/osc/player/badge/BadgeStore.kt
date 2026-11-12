package com.github.tvbox.osc.player.badge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

/**
 * 徽章包存储：负责下载、缓存、加载徽章包 JSON，并匹配媒体徽章。
 *
 * 对应原实现里的 BadgeStore，字段和方法完全一致：
 *   context          上下文
 *   mHandler         主线程 Handler
 *   mPackParser      JSON 解析器
 *   mBadgeMatcher    匹配器
 *   mPackName        当前选中的徽章包名称（供 UI 显示）
 *   mPackVersion     当前选中的徽章包版本
 *   mRuleGroupId     当前选中的分组 id
 *   mBadgePacks      已缓存的徽章包列表
 *   mBadgePackCursor 当前选中的徽章包索引
 */
class BadgeStore(
    private val context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val packParser: BadgePackParser = BadgePackParser,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    private var packName: String = ""
    private var packVersion: String = ""
    private var ruleGroupId: String = ""

    private val badgePacks = mutableListOf<BadgePack>()
    private var badgePackCursor = 0

    private var matcher: BadgeMatcher? = null

    // 当前选中的徽章包（用于 UI 显示）
    val packNameValue: String
        get() = packName
    val packVersionValue: String
        get() = packVersion
    val ruleGroupIdValue: String
        get() = ruleGroupId
    val badgePacksValue: List<BadgePack>
        get() = badgePacks.toList()
    val badgePackCursorValue: Int
        get() = badgePackCursor

    val matcherValue: BadgeMatcher?
        get() = matcher

    /**
     * 加载当前选中的徽章包（从本地或网络）。
     * 读取 HawkConfig.MEDIA_BADGES_SELECTED_LINK 中的来源 URL。
     */
    fun loadSelectedPack() {
        val source = KV.get(HawkConfig.MEDIA_BADGES_SELECTED_LINK, "")
        if (source.isEmpty()) {
            loadFromRawResources()
            return
        }
        downloadPack(source)
    }

    /**
     * 从 res/raw 媒体徽章 JSON 加载（内置 fallback）。
     */
    private fun loadFromRawResources() {
        // 尝试加载 media_badges.json
        val resId = context.resources.getIdentifier("media_badges", "raw", context.packageName)
        if (resId != 0) {
            val text = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
            parseAndSet(text, "内置徽章包", "1.0")
        }
    }

    /**
     * 下载指定 URL 的徽章包 JSON 并解析。
     */
    private fun downloadPack(url: String) {
        job?.cancel()
        job = scope.launch {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.inputStream.bufferedReader().use { reader ->
                    val text = reader.readText()
                    parseAndSet(text, url, "1.0")
                }
            } catch (e: Exception) {
                // 下载失败，尝试从 raw 加载
                handler.post {
                    loadFromRawResources()
                }
            }
        }
    }

    private fun parseAndSet(text: String, source: String, version: String) {
        val result = packParser.parse(text)
        val pack = BadgePack(
            id = source,
            name = source,
            source = source,
        )

        handler.post {
            badgePacks.clear()
            badgePacks.add(pack)
            badgePackCursor = 0
            packName = pack.name
            packVersion = version
            ruleGroupId = if (result.rules.isNotEmpty()) result.rules.first().group else ""
            matcher = BadgeMatcher.create(result.rules)
        }
    }

    /**
     * 匹配给定文本中的徽章。
     */
    fun matchBadges(input: String?): List<BadgeRule> {
        return matcher?.match(input) ?: emptyList()
    }

    /**
     * 清理资源。
     */
    fun destroy() {
        job?.cancel()
        job = null
    }
}
