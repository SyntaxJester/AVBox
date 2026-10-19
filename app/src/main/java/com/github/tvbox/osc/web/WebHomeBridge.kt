package com.github.tvbox.osc.web

import android.webkit.JavascriptInterface
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.util.LOG
import org.json.JSONObject
import java.util.HashMap

/**
 * 与 webhome JS (eclipse.html 等) 通信的 Bridge。
 *
 * JS 调用模式：
 *   w.fm.vodHome({}).then(data => ...)
 *   w.fm.vodCategory(tid, page, {filter, extend}).then(data => ...)
 *   w.fm.search(keyword, {}).then(data => ...)
 *   w.fm.res(url).then(data => ...)
 *   w.fm.toast(text).catch(err => ...)
 *
 * 这里通过 Java @JavascriptInterface + 包装 Promise 来实现。
 * 由于 Android WebView 同步调用无法直接返回 Promise，
 * 我们让 JS 端用 fetch 方式调用，或者直接在桥接里模拟 Promise。
 */
class WebHomeBridge(private val apiConfig: ApiConfig) {

    companion object {
        const val BRIDGE_NAME = "fm"
        private const val TAG = "WebHomeBridge"
    }

    // ===================== 首页数据 =====================

    @JavascriptInterface
    fun vodHome(params: String): String {
        LOG.i("echo-webhome-vodHome: $params")
        return try {
            val sourceBean = apiConfig.getHomeSourceBean()
            val sp = apiConfig.getCSP(sourceBean)
            val result = sp.homeContent(false)
            successResult(result)
        } catch (e: Exception) {
            LOG.e(TAG, "vodHome error: ${e.message}")
            errorResult(e.message ?: "unknown error")
        }
    }

    @JavascriptInterface
    fun vodCategory(tid: String, page: String, params: String): String {
        LOG.i("echo-webhome-vodCategory: tid=$tid page=$page")
        return try {
            val obj = JSONObject(params)
            val filter = obj.optBoolean("filter", false)
            val extendObj = obj.optJSONObject("extend")
            val extend = HashMap<String, String>()
            extendObj?.keys()?.forEach { key ->
                extend[key] = extendObj.getString(key)
            }
            val sourceBean = apiConfig.getHomeSourceBean()
            val sp = apiConfig.getCSP(sourceBean)
            val result = sp.categoryContent(tid, page, filter, extend)
            successResult(result)
        } catch (e: Exception) {
            LOG.e(TAG, "vodCategory error: ${e.message}")
            errorResult(e.message ?: "unknown error")
        }
    }

    @JavascriptInterface
    fun vod(siteKey: String, id: String, name: String, pic: String, params: String): String {
        LOG.i("echo-webhome-vod: siteKey=$siteKey id=$id")
        return try {
            val sourceBean = apiConfig.getSource(siteKey) ?: apiConfig.getHomeSourceBean()
            val sp = apiConfig.getCSP(sourceBean)
            val result = sp.detailContent(listOf(id))
            successResult(result)
        } catch (e: Exception) {
            LOG.e(TAG, "vod error: ${e.message}")
            errorResult(e.message ?: "unknown error")
        }
    }

    @JavascriptInterface
    fun search(keyword: String, params: String): String {
        LOG.i("echo-webhome-search: $keyword")
        return try {
            val sourceBean = apiConfig.getHomeSourceBean()
            val sp = apiConfig.getCSP(sourceBean)
            val result = sp.searchContent(keyword, true)
            successResult(result)
        } catch (e: Exception) {
            LOG.e(TAG, "search error: ${e.message}")
            errorResult(e.message ?: "unknown error")
        }
    }

    // ===================== 播放 =====================

    @JavascriptInterface
    fun play(url: String, flag: String, params: String): String {
        LOG.i("echo-webhome-play: url=$url")
        return try {
            val obj = JSONObject()
            obj.put("code", 1)
            obj.put("url", url)
            obj.put("flag", flag)
            obj.toString()
        } catch (e: Exception) {
            errorResult(e.message ?: "unknown error")
        }
    }

    // ===================== UI 操作 =====================

    @JavascriptInterface
    fun res(url: String, params: String): String {
        LOG.i("echo-webhome-res: $url")
        return try {
            if (url.isEmpty()) return "{}"
            val obj = JSONObject()
            obj.put("url", url)
            obj.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun toast(text: String, params: String): String {
        LOG.i("echo-webhome-toast: $text")
        return "{}"
    }

    @JavascriptInterface
    fun ui_getViewport(params: String): String {
        return """{"width":375,"height":812}"""
    }

    // ===================== 导航 =====================

    @JavascriptInterface
    fun openDetail(params: String): String {
        return "{}"
    }

    @JavascriptInterface
    fun back(params: String): String {
        return "{}"
    }

    // ===================== 私有工具 =====================

    private fun successResult(data: String?): String {
        return JSONObject().apply {
            put("code", if (data != null) 1 else 0)
            put("data", data ?: "")
        }.toString()
    }

    private fun errorResult(msg: String): String {
        return JSONObject().apply {
            put("code", -1)
            put("msg", msg)
        }.toString()
    }
}
