package com.github.tvbox.osc.web

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.PreferenceSettingsActivity
import com.github.tvbox.osc.ui.activity.SearchActivity
import com.github.catvod.crawler.SpiderDebug
import org.json.JSONObject

/**
 * WebHome Bridge - V1 协议实现（与 webhtv 原生实现完全对齐）
 *
 * 协议流程：
 *   1. 页面调用 window.fm.vodHome({}) → Promise
 *   2. JS 侧生成 requestId，登记 pending，调用 native.invoke(requestId, 'vod.home', '{}')
 *   3. 原生处理完成后，在主线程调用 evaluateJavascript:
 *        window.__fmCallback.resolve(requestId, payloadJson)
 *      或 window.__fmCallback.reject(requestId, message, code)
 *   4. 若结果 > INLINE_LIMIT，原生存到 results，仅回传 {"__fmResultId":"..."}
 */
class WebHomeBridge(
    private val activity: Context,
    private val webView: WebView
) {

    companion object {
        private const val CHUNK_SIZE = WebHomeProtocol.CHUNK_SIZE
        private const val INLINE_LIMIT = WebHomeProtocol.INLINE_LIMIT
    }

    private val handler = Handler(Looper.getMainLooper())
    private val results = mutableMapOf<String, String>()

    /**
     * 主入口：页面侧调用后，原生在此处理业务逻辑。
     * 注意：此方法**不返回结果**，结果通过 resolve/reject 回调回传。
     * 业务逻辑在后台线程执行，避免阻塞 WebView 的 JS 线程。
     */
    @JavascriptInterface
    fun invoke(requestId: String, method: String, payloadJson: String) {
        SpiderDebug.log("[webhome] invoke requestId=$requestId method=$method")

        Thread {
            try {
                val payload = if (payloadJson.isNullOrBlank()) JSONObject() else JSONObject(payloadJson)
                val result = executeMethod(method, payload)
                postResolve(requestId, result)
            } catch (e: Exception) {
                SpiderDebug.log("[webhome] invoke failed: ${e.message}")
                postReject(requestId, e.message ?: "Unknown error", "")
            }
        }.start()
    }

    /**
     * 执行具体方法
     */
    private fun executeMethod(method: String, payload: JSONObject): String {
        return when (method) {
            "vod.home" -> handleVodHome(payload)
            "vod.category" -> handleVodCategory(payload)
            "navigation.openDetail" -> handleOpenDetail(payload)
            "player.playVod" -> handlePlayVod(payload)
            "app.search" -> handleSearch(payload)
            "app.openSite" -> handleOpenSite()
            "app.openVod" -> handleOpenVod()
            "app.openSetting" -> handleOpenSetting()
            "ui.getViewport" -> handleGetViewport()
            "ext.toast" -> handleExtToast(payload)
            else -> throw IllegalArgumentException("Unknown method: $method")
        }
    }

    // ==================== 业务方法实现 ====================

    private fun handleVodHome(payload: JSONObject): String {
        // 演示数据 - 模拟真实的 vod.home 响应结构
        // TODO: 接入实际的 SiteApi.homeContent 或 ListLoader
        val sourceName = "演示数据源"
        val classesJson = """[{"typeId":"movie","typeName":"电影"},{"typeId":"series","typeName":"剧集"},{"typeId":"anime","typeName":"动漫"}]"""
        val filtersJson = """{"movie":[{"key":"year","name":"年份","values":[{"name":"全部","value":""},{"name":"2026","value":"2026"},{"name":"2025","value":"2025"}]}]}"""
        
        return """{
            "version": 1,
            "source": {"key": "demo", "name": "$sourceName", "type": 0},
            "client": {"isLeanback": false, "isLandscape": false, "suggestedColumns": 3},
            "classes": $classesJson,
            "filters": $filtersJson,
            "items": [],
            "capabilities": {"category": true, "filters": true, "recommend": true},
            "truncated": false
        }"""
    }

    private fun handleVodCategory(payload: JSONObject): String {
        val typeId = payload.optString("typeId", "")
        val page = payload.optInt("page", 1)
        val filter = payload.optBoolean("filter", false)
        val extend = payload.optJSONObject("extend")?.toString() ?: "{}"
        
        SpiderDebug.log("[webhome] vod.category typeId=$typeId page=$page filter=$filter extend=$extend")
        
        // 演示数据 - 模拟真实的 vod.category 响应结构
        val itemsJson = """[{
            "kind":"vod","vodId":"demo_$page","siteKey":"demo",
            "name":"演示影片 $page","pic":"","remarks":"HD",
            "year":"2026","typeName":"电影","area":"演示区",
            "style":{"type":"rect","ratio":0.714}
        }]"""
        
        return """{
            "version": 1,
            "source": {"key": "demo", "name": "演示数据源", "type": 0},
            "query": {"typeId": "$typeId", "page": $page, "filter": $filter, "extend": $extend},
            "page": $page,
            "pageCount": 1,
            "hasMore": false,
            "truncated": false,
            "items": $itemsJson
        }"""
    }

    private fun handleOpenDetail(payload: JSONObject): String {
        // 打开详情页，实际应该启动 DetailActivity
        val siteKey = payload.optString("siteKey", "")
        val vodId = payload.optString("vodId", "")
        val title = payload.optString("title", "")
        val pic = payload.optString("pic", "")
        
        SpiderDebug.log("[webhome] openDetail siteKey=$siteKey vodId=$vodId title=$title")
        
        // TODO: 启动 DetailActivity 或 DetailActivity.start(activity, ...)
        return "{}"
    }

    private fun handlePlayVod(payload: JSONObject): String {
        // 播放影片，启动 VideoActivity
        val siteKey = payload.optString("siteKey", "")
        val vodId = payload.optString("vodId", "")
        val title = payload.optString("title", "")
        val pic = payload.optString("pic", "")
        val content = payload.optString("content", "")
        val remarks = payload.optString("remarks", "")
        
        SpiderDebug.log("[webhome] playVod siteKey=$siteKey vodId=$vodId title=$title")
        
        // TODO: 启动 VideoActivity
        // val intent = Intent(activity, VideoActivity::class.java).apply {
        //     putExtra("siteKey", siteKey)
        //     putExtra("vodId", vodId)
        //     putExtra("title", title)
        //     putExtra("pic", pic)
        //     putExtra("content", content)
        //     putExtra("remarks", remarks)
        // }
        // activity.startActivity(intent)
        
        return "{}"
    }

    private fun handleSearch(payload: JSONObject): String {
        val keyword = payload.optString("keyword", "")
        SpiderDebug.log("[webhome] search keyword=$keyword")
        
        try {
            val intent = Intent(activity, SearchActivity::class.java)
            activity.startActivity(intent)
        } catch (e: Exception) {
            SpiderDebug.log("[webhome] search failed: ${e.message}")
        }
        
        return "{}"
    }

    private fun handleOpenSite(): String {
        // 打开站点选择页
        SpiderDebug.log("[webhome] openSite")
        try {
            val intent = Intent(activity, MainActivity::class.java)
            intent.putExtra("action", "open_sites")
            activity.startActivity(intent)
        } catch (e: Exception) {
            SpiderDebug.log("[webhome] openSite failed: ${e.message}")
        }
        return "{}"
    }

    private fun handleOpenVod(): String {
        // 打开影片列表页
        SpiderDebug.log("[webhome] openVod")
        try {
            val intent = Intent(activity, MainActivity::class.java)
            activity.startActivity(intent)
        } catch (e: Exception) {
            SpiderDebug.log("[webhome] openVod failed: ${e.message}")
        }
        return "{}"
    }

    private fun handleOpenSetting(): String {
        // 打开设置页
        SpiderDebug.log("[webhome] openSetting")
        try {
            val intent = Intent(activity, PreferenceSettingsActivity::class.java)
            activity.startActivity(intent)
        } catch (e: Exception) {
            SpiderDebug.log("[webhome] openSetting failed: ${e.message}")
        }
        return "{}"
    }

    private fun handleGetViewport(): String {
        // 返回视口信息
        val width = webView.width
        val height = webView.height
        return """{
            "width": $width,
            "height": $height,
            "safeTop": 0,
            "statusBarHeight": 0
        }"""
    }

    private fun handleExtToast(payload: JSONObject): String {
        val message = payload.optString("message", "")
        SpiderDebug.log("[webhome] toast: $message")
        
        // TODO: 显示 Toast
        // Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        
        return "{}"
    }

    // ==================== 结果分片 API ====================

    @JavascriptInterface
    fun resultLength(id: String): Int {
        val result = synchronized(results) { results[id] }
        return result?.length ?: 0
    }

    @JavascriptInterface
    fun resultChunk(id: String, start: Int): String {
        val result = synchronized(results) { results[id] } ?: return ""
        if (start < 0 || start >= result.length) return ""
        return result.substring(start, minOf(start + CHUNK_SIZE, result.length))
    }

    @JavascriptInterface
    fun clearResult(id: String) {
        synchronized(results) { results.remove(id) }
    }

    // ==================== 资源代理 API ====================

    @JavascriptInterface
    fun resourceUrl(url: String, optionsJson: String): String {
        // 资源代理 URL（用于图片等）
        // TODO: 实现实际的资源代理逻辑
        return url
    }

    // ==================== 回调方法 ====================

    /**
     * 把任意字符串安全地编码为 JS 字面量（含首尾引号）。
     * 直接拼字符串会在遇到单引号/换行/反斜杠时破坏脚本，必须走 JSON 编码。
     */
    private fun jsQuote(value: String): String {
        return JSONObject.quote(value)
    }

    /**
     * 成功回调：页面侧调用 window.__fmCallback.resolve(requestId, payload)
     */
    private fun postResolve(requestId: String, payload: String) {
        handler.post {
            // 检查是否需要分片
            val finalPayload: String
            if (payload.length > INLINE_LIMIT) {
                val resultId = "${requestId}_${System.nanoTime()}"
                synchronized(results) { results[resultId] = payload }
                finalPayload = "{\"__fmResultId\":${jsQuote(resultId)}}"
            } else {
                finalPayload = payload.ifBlank { "null" }
            }

            val script = "window.${WebHomeProtocol.CALLBACK_HOST}" +
                "&&window.${WebHomeProtocol.CALLBACK_HOST}.resolve(${jsQuote(requestId)},$finalPayload)"
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * 失败回调：页面侧调用 window.__fmCallback.reject(requestId, message, code)
     */
    private fun postReject(requestId: String, message: String, code: String) {
        handler.post {
            val script = "window.${WebHomeProtocol.CALLBACK_HOST}" +
                "&&window.${WebHomeProtocol.CALLBACK_HOST}.reject(" +
                "${jsQuote(requestId)},${jsQuote(message)},${jsQuote(code)})"
            webView.evaluateJavascript(script, null)
        }
    }
}